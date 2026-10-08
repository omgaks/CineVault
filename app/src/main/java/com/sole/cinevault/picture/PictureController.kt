package com.sole.cinevault.picture

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.sole.cinevault.CineVaultToast

sealed interface PictureAvailability {
    object Available : PictureAvailability
    data class Unavailable(val reason: String) : PictureAvailability
}

/** Lets small UI spots (the top-right "Picture" button) find the controller without plumbing. */
object PictureEnhanceRegistry {
    var current: PictureEnhanceController? by mutableStateOf(null)
}

/**
 * Owns Picture enhancement for the player: settings, per-title memory, the GPU effect, and
 * the safety guards.
 *
 * Design rules learned from device testing:
 *  - The Media3 effect list is changed at most ONCE (the first time Picture is switched on
 *    for a title, before the first prepare() when the choice was saved). After that every
 *    change — presets, sliders, Off, hold-to-compare, split view — only changes shader
 *    uniforms, so nothing ever touches the running video pipeline.
 *  - If the effect pipeline itself fails, the failure is caught here before the player's
 *    own error handling sees it: the effect is removed, playback resumes at the same
 *    position, and the reason is shown in the panel instead of freezing the movie.
 */
@OptIn(UnstableApi::class)
class PictureEnhanceController(
    private val context: Context,
    private val player: ExoPlayer,
    initialPath: String? = null,
) {
    var settings by mutableStateOf(PictureSettings())
        private set
    var panelOpen by mutableStateOf(false)
    var comparing by mutableStateOf(false)
        private set
    var splitView by mutableStateOf(false)
        private set
    /** Where the split-view divider sits (0..1 across the picture); the user can drag it. */
    var splitPosition by mutableStateOf(0.5f)
        private set
    var detected by mutableStateOf(PictureContent.FILM)
        private set
    var availability by mutableStateOf<PictureAvailability>(PictureAvailability.Available)
        private set
    /** Explains an automatic pause ("Phone is hot…"), shown in the panel. */
    var note by mutableStateOf<String?>(null)
        private set
    /** Technical reason the last effect failure happened, shown in the panel. */
    var lastError by mutableStateOf<String?>(null)
        private set
    /** "Live · 24 fps", "Off", "Waiting…" … proof that frames really go through the shader. */
    var statusLine by mutableStateOf("Off")
        private set
    var statusLive by mutableStateOf(false)
        private set

    val isActive: Boolean
        get() = settings.preset != PicturePreset.OFF && availability is PictureAvailability.Available

    private val live = PictureLiveParams()
    private val effect = PictureEnhanceEffect(live)
    private val performance = PicturePerformanceRuntime()
    private var performancePlan = performance.plan(settings.intensity)

    private var pipelineInstalled = false
    private var installedAtMs = 0L
    private var badDropWindows = 0
    private var currentPath = ""
    private var setupFailed = false
    private var failureHandled = false
    private var lockedReason: String? = null
    private var lastPreset = PicturePreset.NATURAL
    private var thermalListener: Any? = null
    private var lastFrames = 0L
    private var lastPollAtMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updatePerformanceSource()
            evaluateAvailability()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updatePerformanceSource()
            evaluateAvailability()
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            if (!pipelineInstalled || !isActive || comparing) return
            // Analytics reports dropped frames in an elapsed window; estimate total frames
            // from source fps, including the dropped frames themselves.
            val sourceFps = player.videoFormat?.frameRate?.takeIf { it.isFinite() && it > 0f } ?: 30f
            val renderedEstimate = (sourceFps * elapsedMs.coerceAtLeast(0L) / 1000f).toInt().coerceAtLeast(0)
            performance.recordWindow(droppedFrames, renderedEstimate + droppedFrames.coerceAtLeast(0))
            updatePerformancePlan()
            // Ignore the start-up / seek burst right after the effect is installed.
            if (SystemClock.elapsedRealtime() - installedAtMs < 6_000L) return
            badDropWindows = if (droppedFrames >= 8) badDropWindows + 1 else maxOf(0, badDropWindows - 1)
            if (badDropWindows >= 3) {
                pauseAutomatically("Picture enhancement paused to keep playback smooth")
            }
        }
    }

    init {
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        if (Build.VERSION.SDK_INT >= 29) registerThermalListener()

        // If this title was left on, install the pipeline NOW — before the first prepare() —
        // so opening it never needs a restart.
        if (!initialPath.isNullOrEmpty()) {
            val saved = PictureMemory.load(context, initialPath)
            if (saved != null && saved.preset != PicturePreset.OFF) {
                currentPath = initialPath
                settings = saved
                if (saved.preset != PicturePreset.CUSTOM) lastPreset = saved.preset
                refreshLive()
                installPipeline(restart = false)
            }
        }
        evaluateAvailability()
    }

    // ── Video binding ────────────────────────────────────────────────────────────

    fun bindVideo(path: String, fileName: String, genres: List<String>) {
        detected = PictureContentDetector.detect(fileName, genres)
        if (path == currentPath) return
        currentPath = path
        lockedReason = null
        note = null
        comparing = false
        splitView = false
        badDropWindows = 0
        performance.reset()
        live.performanceScale = 1f
        updatePerformanceSource()
        settings = PictureMemory.load(context, path) ?: PictureSettings()
        if (settings.preset != PicturePreset.OFF && settings.preset != PicturePreset.CUSTOM) {
            lastPreset = settings.preset
        }
        evaluateAvailability()
    }

    // ── User actions ─────────────────────────────────────────────────────────────

    fun togglePanel() {
        if (panelOpen) closePanel() else panelOpen = true
    }

    fun closePanel() {
        // End transient comparison modes before removing the panel host so the shader receives
        // a normal full-frame state while the UI is still alive.
        comparing = false
        splitView = false
        refreshLive()
        panelOpen = false
    }

    fun setEnabled(on: Boolean) {
        if (on) {
            selectPreset(if (lastPreset == PicturePreset.OFF) PicturePreset.NATURAL else lastPreset)
        } else {
            if (settings.preset != PicturePreset.OFF && settings.preset != PicturePreset.CUSTOM) {
                lastPreset = settings.preset
            }
            settings = settings.copy(preset = PicturePreset.OFF)
            note = null
            splitView = false
            persist()
            applyEffects()
        }
    }

    fun selectPreset(preset: PicturePreset) {
        if (preset == PicturePreset.OFF) {
            setEnabled(false)
            return
        }
        note = null
        if (preset != PicturePreset.CUSTOM) lastPreset = preset
        settings = PictureProfiles.withPreset(
            settings,
            preset,
            PictureProfiles.resolveContent(settings.content, detected),
        )
        persist()
        applyEffects()
    }

    fun setContent(content: PictureContent) {
        settings = settings.copy(content = content)
        // A named preset follows the content type; Custom keeps the user's sliders.
        if (settings.preset != PicturePreset.OFF && settings.preset != PicturePreset.CUSTOM) {
            settings = PictureProfiles.withPreset(
                settings,
                settings.preset,
                PictureProfiles.resolveContent(content, detected),
            )
        }
        persist()
        refreshLive()
    }

    fun setIntensity(value: Float) {
        settings = settings.copy(intensity = value.coerceIn(0f, 1f))
        refreshLive()
    }

    fun setFineTune(
        sharpen: Float = settings.sharpen,
        deband: Float = settings.deband,
        colour: Float = settings.colour,
        grain: Float = settings.grain,
    ) {
        settings = settings.copy(
            preset = if (settings.preset == PicturePreset.OFF) PicturePreset.OFF else PicturePreset.CUSTOM,
            sharpen = sharpen.coerceIn(0f, 1f),
            deband = deband.coerceIn(0f, 1f),
            colour = colour.coerceIn(0f, 1f),
            grain = grain.coerceIn(0f, 1f),
        )
        refreshLive()
    }

    /** Fine-tune sliders back to the current look's own values. */
    fun resetFineTune() {
        settings = PictureProfiles.resetFineTune(
            settings,
            PictureProfiles.resolveContent(settings.content, detected),
            lastPreset,
        )
        persist()
        refreshLive()
    }

    /** Everything back to defaults; Picture stays on or off as it was. */
    fun resetAll() {
        // Reset is a live-uniform operation. Never touch/rebuild the running Media3 pipeline here:
        // doing so can disturb playback while Split View is active.
        comparing = false
        splitView = false
        settings = PictureProfiles.resetAll(settings, detected)
        note = null
        if (settings.preset != PicturePreset.OFF) lastPreset = PicturePreset.NATURAL
        persist()
        refreshLive()
    }

    /** Call when a slider drag ends. */
    fun commit() {
        persist()
    }

    fun holdCompare(value: Boolean) {
        comparing = value
        refreshLive()
    }

    fun toggleSplit() {
        splitView = !splitView
        refreshLive()
    }

    fun moveSplit(value: Float) {
        splitPosition = value.coerceIn(0.08f, 0.92f)
        refreshLive()
    }

    /**
     * After a rotation while PAUSED, the effect pipeline keeps showing the last frame at the old
     * output size until a new frame is produced. Re-showing the current frame fixes that without
     * the user having to press play.
     */
    suspend fun refreshFrame() {
        if (!pipelineInstalled) return
        if (player.playbackState != Player.STATE_READY || player.playWhenReady) return
        // A paused movie only redraws when a NEW frame arrives. Seeking to the same spot was not
        // enough on device, so play for a split second with the sound off, then pause and return
        // to exactly where it was. The picture then re-renders at the new size.
        val position = player.currentPosition
        val volume = player.volume
        player.volume = 0f
        player.playWhenReady = true
        kotlinx.coroutines.delay(260)
        if (player.playWhenReady) {
            player.playWhenReady = false
            player.seekTo(position)
        }
        player.volume = volume
    }

    /** Called by the panel while it is open so the status line reflects reality. */
    fun refreshActivity() {
        val now = SystemClock.elapsedRealtime()
        val frames = live.frames
        val dt = now - lastPollAtMs
        val delta = frames - lastFrames
        lastFrames = frames
        lastPollAtMs = now
        val fps = if (dt in 1..5000 && delta >= 0) (delta * 1000f / dt) else 0f

        when {
            !isActive -> {
                statusLive = false
                statusLine = if (availability is PictureAvailability.Unavailable) "Not available" else "Off"
            }
            !pipelineInstalled -> {
                statusLive = false
                statusLine = "Starting…"
            }
            lockedReason != null -> {
                statusLive = false
                statusLine = "Stopped"
            }
            fps >= 1f -> {
                statusLive = true
                statusLine = "Live · ${fps.toInt()} fps through the GPU filter"
            }
            player.isPlaying && now - installedAtMs > 3_000L -> {
                statusLive = false
                statusLine = "Not running — no frames reached the filter"
            }
            else -> {
                statusLive = false
                statusLine = if (player.isPlaying) "Starting…" else "Paused"
            }
        }
    }

    /**
     * Called first thing from the player's error handler. Returns true when the failure was
     * caused by the picture filter and has been fully handled here (effect removed, playback
     * resumed), so the normal retry / software-fallback / error-screen logic must not run.
     */
    fun consumeEffectFailure(error: PlaybackException): Boolean {
        if (!pipelineInstalled || failureHandled) return false
        val code = error.errorCode
        val sinceInstall = SystemClock.elapsedRealtime() - installedAtMs
        val frameProcessing =
            code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
        val decoderRightAfterInstall = code in 4001..4005 && sinceInstall < 20_000L
        if (!frameProcessing && !decoderRightAfterInstall) return false

        failureHandled = true
        val cause = generateSequence<Throwable>(error) { it.cause }.last().message.orEmpty()
        lastError = "${error.errorCodeName}${if (cause.isNotBlank()) ": " + cause.take(140) else ""}"
        lockedReason = "Stopped: the GPU filter failed on this video"
        availability = PictureAvailability.Unavailable(lockedReason.orEmpty())
        settings = settings.copy(preset = PicturePreset.OFF)
        persist() // forgets the saved "on" so the title opens normally next time
        live.current = PictureShaderParams.OFF
        live.performanceScale = 1f
        try {
            player.setVideoEffects(emptyList())
        } catch (_: Throwable) {
        }
        pipelineInstalled = false
        CineVaultToast.show(context, "Picture enhancement stopped — your movie continues", long = true)

        val resumeAt = player.currentPosition.coerceAtLeast(0L)
        val resumePlaying = player.playWhenReady
        player.prepare()
        player.seekTo(resumeAt)
        player.playWhenReady = resumePlaying
        return true
    }

    fun release() {
        try {
            player.removeListener(playerListener)
            player.removeAnalyticsListener(analyticsListener)
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= 29) unregisterThermalListener()
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private fun persist() {
        if (currentPath.isNotEmpty()) PictureMemory.save(context, currentPath, settings)
    }

    private fun refreshLive() {
        updatePerformancePlan()
        live.current = PictureProfiles.toShaderParams(settings, comparing, isActive, splitView, splitPosition)
    }

    private fun updatePerformanceSource() {
        val format = player.videoFormat ?: return
        performance.setSource(format.width, format.height, format.frameRate)
        updatePerformancePlan()
    }

    private fun updatePerformancePlan() {
        performancePlan = performance.plan(settings.intensity)
        // At normal load retain the exact P6 appearance, even at low user intensity.
        // Under pressure reduce only optional repair/reconstruction strength.
        // Never alter user sliders, the Movie sharpen ceiling, or Media3 effects.
        live.performanceScale = when (performancePlan.load) {
            PictureAdaptivePerformancePolicy.Load.NORMAL -> 1f
            PictureAdaptivePerformancePolicy.Load.ELEVATED,
            PictureAdaptivePerformancePolicy.Load.CRITICAL -> performancePlan.repairScale
        }
    }

    private fun evaluateAvailability() {
        if (!setupFailed) {
            val format = player.videoFormat
            val reason: String? = when {
                lockedReason != null -> lockedReason
                format == null -> null
                PictureHdrRouting.decide(format).route == PictureHdrPolicy.Route.HDR_PASSTHROUGH ->
                    "HDR or unknown colour transfer — original playback protected"
                format.height > 1440 || format.width > 2560 -> "Not needed for 4K video"
                else -> null
            }
            val next: PictureAvailability =
                if (reason == null) PictureAvailability.Available else PictureAvailability.Unavailable(reason)
            if (next != availability) availability = next
        }
        applyEffects()
    }

    private fun applyEffects() {
        refreshLive() // values must be ready before the first frame reaches the shader
        if (isActive && !pipelineInstalled && !failureHandled) {
            installPipeline(restart = true)
        }
    }

    /**
     * The one and only change to the Media3 effect list. [restart] is needed when the player
     * is already prepared, because the renderer builds its effect pipeline when it is enabled.
     */
    private fun installPipeline(restart: Boolean) {
        try {
            val needsRestart = restart && player.playbackState != Player.STATE_IDLE
            val resumeAt = player.currentPosition.coerceAtLeast(0L)
            val resumePlaying = player.playWhenReady
            player.setVideoEffects(listOf(effect))
            pipelineInstalled = true
            installedAtMs = SystemClock.elapsedRealtime()
            badDropWindows = 0
            performance.resetWindow()
            if (needsRestart) {
                player.stop()
                player.prepare()
                player.seekTo(resumeAt)
                player.playWhenReady = resumePlaying
            }
        } catch (_: Throwable) {
            setupFailed = true
            pipelineInstalled = false
            availability = PictureAvailability.Unavailable("Video effects aren't available here")
        }
    }

    /** Switches the filter off without forgetting the user's saved choice for this title. */
    private fun pauseAutomatically(reason: String) {
        if (!isActive) return
        if (settings.preset != PicturePreset.CUSTOM) lastPreset = settings.preset
        settings = settings.copy(preset = PicturePreset.OFF)
        note = reason
        CineVaultToast.show(context, reason)
        refreshLive()
    }

    @RequiresApi(29)
    private fun registerThermalListener() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            performance.setThermalStatus(status)
            updatePerformancePlan()
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                pauseAutomatically("Phone is hot — picture enhancement paused")
            }
        }
        thermalListener = listener
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
    }

    @RequiresApi(29)
    private fun unregisterThermalListener() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let {
            pm.removeThermalStatusListener(it)
        }
        thermalListener = null
    }
}
