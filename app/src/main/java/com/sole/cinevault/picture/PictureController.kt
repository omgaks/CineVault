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
import androidx.media3.common.MimeTypes
import androidx.media3.common.ColorInfo
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
 * Owns Picture enhancement for the player: settings, per-title memory, the GPU effect
 * attachment, and the safety guards (HDR / 4K / decoder errors / dropped frames / heat).
 *
 * Slider moves and hold-to-compare only change [PictureLiveParams] — no pipeline rebuild.
 *
 * The Media3 effect pipeline is installed lazily, the first time the user turns Picture on
 * for this player instance, so normal playback (HDR, glasses mode, direct surface output)
 * is untouched until then. Installing it needs one quick playback restart (stop + prepare at
 * the same position); after that, on/off and tuning are instant. Media3 offers no way to
 * remove the pipeline again, so it stays until the player screen closes.
 */
@OptIn(UnstableApi::class)
class PictureEnhanceController(
    private val context: Context,
    private val player: ExoPlayer,
) {
    var settings by mutableStateOf(PictureSettings())
        private set
    var panelOpen by mutableStateOf(false)
    var comparing by mutableStateOf(false)
        private set
    var detected by mutableStateOf(PictureContent.FILM)
        private set
    var availability by mutableStateOf<PictureAvailability>(PictureAvailability.Available)
        private set
    /** Explains an automatic pause ("Phone is hot…"), shown in the panel. */
    var note by mutableStateOf<String?>(null)
        private set

    val isActive: Boolean
        get() = settings.preset != PicturePreset.OFF && availability is PictureAvailability.Available

    private val live = PictureLiveParams()
    private val effect = PictureEnhanceEffect(live)

    private var attached = false
    private var attachedAtMs = 0L
    private var badDropWindows = 0
    private var currentPath = ""
    private var setupFailed = false
    private var pipelineInstalled = false
    private var lockedReason: String? = null
    private var lastPreset = PicturePreset.NATURAL
    private var thermalListener: Any? = null

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            evaluateAvailability()
        }

        override fun onTracksChanged(tracks: Tracks) {
            evaluateAvailability()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!attached) return
            val code = error.errorCode
            if (code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
                code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED
            ) {
                lockedReason = "Not supported for this video"
                CineVaultToast.show(context, "Picture enhancement isn't supported for this video")
                evaluateAvailability()
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            if (!attached || comparing) return
            // Ignore the start-up / seek burst right after the effect is attached.
            if (SystemClock.elapsedRealtime() - attachedAtMs < 5_000L) return
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
        badDropWindows = 0
        settings = PictureMemory.load(context, path) ?: PictureSettings()
        if (settings.preset != PicturePreset.OFF && settings.preset != PicturePreset.CUSTOM) {
            lastPreset = settings.preset
        }
        evaluateAvailability()
    }

    // ── User actions ─────────────────────────────────────────────────────────────

    fun togglePanel() {
        panelOpen = !panelOpen
    }

    fun closePanel() {
        panelOpen = false
        comparing = false
        refreshLive()
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

    /** Call when a slider drag ends. */
    fun commit() {
        persist()
    }

    fun holdCompare(value: Boolean) {
        comparing = value
        refreshLive()
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
        live.current = PictureProfiles.toShaderParams(settings, comparing, isActive)
    }

    private fun isHdr(): Boolean {
        val format = player.videoFormat ?: return false
        return ColorInfo.isTransferHdr(format.colorInfo) ||
            format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
    }

    private fun evaluateAvailability() {
        if (!setupFailed) {
            val format = player.videoFormat
            val reason: String? = when {
                lockedReason != null -> lockedReason
                format == null -> null
                isHdr() -> "Not available for HDR / Dolby Vision video"
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
        val want = isActive
        refreshLive() // values must be ready before the first frame reaches the shader
        if (want && !attached) {
            try {
                val needsRestart = !pipelineInstalled && player.playbackState != Player.STATE_IDLE
                player.setVideoEffects(listOf(effect))
                pipelineInstalled = true
                attached = true
                attachedAtMs = SystemClock.elapsedRealtime()
                badDropWindows = 0
                if (needsRestart) {
                    // The renderer only builds its effect pipeline when it is (re-)enabled.
                    // stop() resets it and keeps the playlist and position; prepare() resumes.
                    val resume = player.playWhenReady
                    player.stop()
                    player.prepare()
                    player.playWhenReady = resume
                }
            } catch (_: Throwable) {
                setupFailed = true
                availability = PictureAvailability.Unavailable("Video effects aren't available here")
            }
        } else if (!want && attached) {
            try {
                player.setVideoEffects(emptyList())
            } catch (_: Throwable) {
            }
            attached = false
        }
    }

    /** Switches enhancement off without forgetting the user's saved choice for this title. */
    private fun pauseAutomatically(reason: String) {
        if (!isActive) return
        if (settings.preset != PicturePreset.CUSTOM) lastPreset = settings.preset
        settings = settings.copy(preset = PicturePreset.OFF)
        note = reason
        CineVaultToast.show(context, reason)
        applyEffects()
    }

    @RequiresApi(29)
    private fun registerThermalListener() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
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
