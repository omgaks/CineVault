package com.sole.cinevault.audiofx

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.subtitles.AutoSyncAudioExtractor
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * AudioFxController.kt
 *
 * Phase 0 of the audio-enhancement work: android.media.audiofx, not a
 * custom native DSP engine. This is the framework Android itself has
 * shipped since API 9 (Equalizer/BassBoost/Virtualizer/PresetReverb/
 * LoudnessEnhancer) — free, zero native code, zero third-party licensing
 * to reason about, and it attaches directly to any player's audio session
 * ID rather than needing to sit inside the decode pipeline. It won't match
 * a 32-band native engine's precision, but it's real, it's already on
 * every Android device, and it's honestly verifiable against Android's own
 * documented contract for these classes — unlike a native DSP chain, whose
 * correctness genuinely can't be confirmed without listening on real
 * hardware. If a fuller native engine is built later, this controller and
 * its dashboard can be swapped out or layered underneath it without
 * touching the rest of the player.
 *
 * Effects are tied to ExoPlayer's audioSessionId, NOT to a specific
 * MediaItem or the player instance itself. ExoPlayer can generate a new
 * session id when its internal audio track is recreated (format changes,
 * some device/OS-level audio routing changes) — Player.Listener.
 * onAudioSessionIdChanged is the documented signal for this, so this
 * controller listens for it and rebuilds all five effects against the new
 * session rather than assuming the id is stable for the player's whole
 * lifetime.
 *
 * Settings are persisted per-app (not per-video) in SharedPreferences,
 * matching the pattern already used elsewhere in this codebase (subtitle
 * behavior prefs, secret-folder prefs) — a listener preference like "I like
 * +4dB at 1kHz" is something a person sets once and expects to carry
 * across every video, not something scoped to one file.
 */
/**
 * Coarse output-device categories a preset can be auto-assigned to.
 * Deliberately coarse (4 buckets, not one per AudioDeviceInfo.TYPE_*
 * constant) — "my wired earbuds" and "my other wired earbuds" don't need
 * separate profiles for this to be useful; the categories that actually
 * matter for EQ/FX choice are speaker vs. wired vs. Bluetooth vs. USB.
 */
internal enum class AudioOutputProfile(val label: String) {
    SPEAKER("Speaker"),
    WIRED("Wired Headphones"),
    BLUETOOTH("Bluetooth"),
    USB_DAC("USB / USB-C DAC");

    companion object {
        fun fromDeviceType(type: Int): AudioOutputProfile? = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BLUETOOTH
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> USB_DAC
            else -> null
        }

        // Bluetooth/USB DACs are the ones worth reacting to first — a
        // person plugging in real headphones almost always wants THAT
        // preset over whatever the speaker was doing; speaker is the
        // fallback once nothing higher-priority remains connected.
        val priorityOrder = listOf(BLUETOOTH, USB_DAC, WIRED, SPEAKER)
    }
}

/**
 * A named, complete snapshot of every tunable value this controller
 * exposes — everything needed to reproduce a sound exactly, bundled so
 * "my gym Bluetooth earbuds preset" is one object, not seven separate
 * prefs someone has to remember to set together.
 */
internal data class AudioFxPreset(
    val name: String,
    val enabled: Boolean,
    val bandLevelsDb: List<Int>,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val reverbPreset: Short,
    val loudnessGainMillibels: Int,
    val compressorAmount: Int,
    val limiterEnabled: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("enabled", enabled)
        put("bandLevelsDb", JSONArray(bandLevelsDb))
        put("bassBoostStrength", bassBoostStrength)
        put("virtualizerStrength", virtualizerStrength)
        put("reverbPreset", reverbPreset.toInt())
        put("loudnessGainMillibels", loudnessGainMillibels)
        put("compressorAmount", compressorAmount)
        put("limiterEnabled", limiterEnabled)
    }

    companion object {
        fun fromJson(json: JSONObject): AudioFxPreset? = runCatching {
            val bandsJson = json.getJSONArray("bandLevelsDb")
            AudioFxPreset(
                name = json.getString("name"),
                enabled = json.getBoolean("enabled"),
                bandLevelsDb = (0 until bandsJson.length()).map { bandsJson.getInt(it) },
                bassBoostStrength = json.getInt("bassBoostStrength"),
                virtualizerStrength = json.getInt("virtualizerStrength"),
                reverbPreset = json.getInt("reverbPreset").toShort(),
                loudnessGainMillibels = json.getInt("loudnessGainMillibels"),
                compressorAmount = json.optInt("compressorAmount", 0),
                limiterEnabled = json.optBoolean("limiterEnabled", false)
            )
        }.getOrNull()
    }
}

internal class AudioFxController(private val context: Context) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    // API 28+ only — a real multi-band-EQ -> multi-band-compressor ->
    // limiter mastering chain, built into Android since Oreo. Null below
    // that, and every setter is a safe no-op in that case (checked at the
    // call site, not left to throw) — an older device just doesn't get
    // this tier, same as any other Build.VERSION-gated feature elsewhere
    // in this app.
    private var dynamicsProcessing: DynamicsProcessing? = null
    // Real-time waveform/FFT capture from the actual audio stream — not
    // simulated bars. API 9+, so no version gate needed here.
    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cinevault_audio_fx", Context.MODE_PRIVATE)

    // Exposed for the dashboard to react to without needing its own
    // polling — updated on attach() and whenever a setter below runs.
    var enabled by mutableStateOf(loadEnabled())
        private set
    var bandLevelsDb by mutableStateOf(loadBandLevels())
        private set
    var bassBoostStrength by mutableStateOf(loadIntPref(KEY_BASS_STRENGTH, 0))
        private set
    var virtualizerStrength by mutableStateOf(loadIntPref(KEY_VIRTUALIZER_STRENGTH, 0))
        private set
    var reverbPreset by mutableStateOf(loadIntPref(KEY_REVERB_PRESET, PresetReverb.PRESET_NONE.toInt()).toShort())
        private set
    var loudnessGainMillibels by mutableStateOf(loadIntPref(KEY_LOUDNESS_MB, 0))
        private set
    // 0-100 — a single overall "how hard should the compressor squeeze"
    // dial, applied uniformly across all MBC bands. DynamicsProcessing
    // exposes far more (per-band threshold/ratio/attack/release/knee/
    // noise-gate/expander independently), but a per-band mixing-console UI
    // is a different, bigger piece of work than this pass — this dial is
    // the genuinely useful 80% of it: 0 = compressor bypassed (bands left
    // at their device-default thresholds), 100 = aggressive.
    var compressorAmount by mutableStateOf(loadIntPref(KEY_COMPRESSOR_AMOUNT, 0))
        private set
    var limiterEnabled by mutableStateOf(loadEnabled(KEY_LIMITER_ENABLED))
        private set
    val dynamicsProcessingAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // Latest captured waveform (PCM8, unsigned) — updated ~20x/second while
    // a Visualizer is attached and enabled. Dashboard reads this directly;
    // no separate polling loop needed since Compose recomposes on read.
    var waveform: ByteArray by mutableStateOf(ByteArray(0))
        private set
    var fft: ByteArray by mutableStateOf(ByteArray(0))
        private set

    // Populated once an Equalizer exists — the number of bands and each
    // band's center frequency are fixed properties of the device's
    // Equalizer implementation, not something this controller invents.
    var bandCount: Short = 0
        private set
    var bandLevelRangeDb: IntRange = 0..0
        private set
    var bandCenterFreqHz: List<Int> = emptyList()
        private set

    // Adaptive Audio (Beta) — automatically nudges the compressor,
    // limiter, and vocal-presence EQ toward a scene-appropriate target
    // based on real-time analysis of the actual audio, rather than
    // requiring the user to guess and manually switch. See
    // AudioSceneAnalyzer.kt for what "real-time analysis" actually means
    // here and its honest limits.
    //
    // CRITICAL DESIGN RULE: adaptive mode NEVER calls the persisting
    // public setters (setBandLevel/setCompressorAmount/setLimiterEnabled)
    // — doing so would silently overwrite the user's own manually-tuned
    // preference in SharedPreferences every time the scene changes,
    // corrupting what they'd get back the moment they turned adaptive
    // mode off. Instead, live adjustments call the native effect objects
    // directly (equalizer?.setBandLevel, the existing applyCompressor
    // Amount/applyLimiterEnabled private helpers) and are interpolated
    // smoothly toward each scene's target rather than snapped, so a scene
    // change is inaudible-transition, not a jump. Turning adaptive mode
    // off calls restoreManualBaseline(), which re-applies the user's
    // actual persisted values and clears the overlay entirely.
    var adaptiveAudioEnabled: Boolean by mutableStateOf(loadEnabled(KEY_ADAPTIVE_ENABLED))
        private set
    var detectedScene: AudioScene by mutableStateOf(AudioScene.NEUTRAL)
        private set
    var sceneDebugText: String by mutableStateOf("")
        private set

    private val sceneAnalyzer = AudioSceneAnalyzer()
    private var liveVocalBoostDb = 0f
    private var liveCompressorAmount = 0f

    // Named presets and per-output-device auto-assignment. Independent of
    // the effects themselves (a preset is just a bundle of the same values
    // above) and independent of the player — registered once for the
    // controller's whole lifetime via startAutoSwitching(), not re-done
    // per audio session.
    var presets: List<AudioFxPreset> by mutableStateOf(loadPresets())
        private set
    var profileAssignments: Map<AudioOutputProfile, String> by mutableStateOf(loadProfileAssignments())
        private set
    // The profile this controller currently believes is active — a
    // heuristic, not a guaranteed-accurate query of Android's actual
    // routing decision (Android doesn't expose one to a normal app).
    // Updated by the same "newly added device wins, priority order on
    // removal" pattern Google's own Wear OS audio guide uses for this
    // exact purpose. Exposed so the dashboard can show what CineVault
    // thinks is currently plugged in.
    var currentOutputProfile: AudioOutputProfile? by mutableStateOf(null)
        private set

    private var audioManager: AudioManager? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val candidate = addedDevices
                .mapNotNull { AudioOutputProfile.fromDeviceType(it.type) }
                .minByOrNull { AudioOutputProfile.priorityOrder.indexOf(it) }
            if (candidate != null) {
                applyOutputProfile(candidate)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val removedProfiles = removedDevices.mapNotNull { AudioOutputProfile.fromDeviceType(it.type) }
            val active = currentOutputProfile
            if (active != null && active in removedProfiles) {
                applyOutputProfile(resolveActiveOutputProfile())
            }
        }
    }

    /**
     * Registers the output-device watcher. Call once, independent of
     * attachToPlayer() — this reacts to system-level output changes, not
     * anything tied to a specific playback session. Safe to call multiple
     * times; re-registering the same callback instance is a no-op on
     * Android's side per AudioManager's own documented contract.
     */
    fun startAutoSwitching() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        runCatching { manager.registerAudioDeviceCallback(audioDeviceCallback, null) }
        applyOutputProfile(resolveActiveOutputProfile())
    }

    fun stopAutoSwitching() {
        audioManager?.let { runCatching { it.unregisterAudioDeviceCallback(audioDeviceCallback) } }
        audioManager = null
    }

    private fun resolveActiveOutputProfile(): AudioOutputProfile? {
        val manager = audioManager ?: return null
        val devices = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull() ?: return null
        val profiles = devices.mapNotNull { AudioOutputProfile.fromDeviceType(it.type) }
        return AudioOutputProfile.priorityOrder.firstOrNull { it in profiles }
    }

    private fun applyOutputProfile(profile: AudioOutputProfile?) {
        currentOutputProfile = profile
        val presetName = profile?.let { profileAssignments[it] } ?: return
        applyPreset(presetName)
    }

    // Loudness normalization (ReplayGain-style) — see LoudnessAnalyzer.kt
    // for the actual BS.1770 measurement. A stored player reference is
    // needed here specifically because applying the computed gain is
    // two-sided: LoudnessEnhancer (already used elsewhere in this
    // controller) can only ever BOOST, never attenuate — there's no
    // "negative loudness enhancer." A track measured LOUDER than the
    // target needs the opposite: reducing Player.volume. Neither tool
    // alone covers both directions; this controller already touches the
    // player for its audioSessionId, so storing the reference here (set
    // in attachToPlayer, cleared in detachFromPlayer) is the natural place
    // for the other half of that same relationship, not a new one.
    private var currentPlayer: ExoPlayer? = null
    var loudnessNormalizationEnabled by mutableStateOf(loadEnabled(KEY_LOUDNESS_NORM_ENABLED))
        private set
    var isAnalyzingLoudness by mutableStateOf(false)
        private set
    var lastMeasuredLufs by mutableStateOf<Double?>(null)
        private set
    var lastAppliedGainDb by mutableStateOf<Double?>(null)
        private set
    private var lastAnalyzedVideoPath: String? = null

    val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attach(audioSessionId)
        }
    }

    fun attachToPlayer(player: ExoPlayer) {
        currentPlayer = player
        player.addListener(playerListener)
        if (player.audioSessionId != 0) {
            attach(player.audioSessionId)
        }
    }

    fun detachFromPlayer(player: ExoPlayer) {
        player.removeListener(playerListener)
        // Restore normal volume before letting go — a stale attenuation
        // left on the player after this controller stops managing it
        // would silently make the NEXT video (or whatever reuses this
        // player) quieter than it should be, for a reason nothing would
        // explain.
        if (currentPlayer === player) {
            player.volume = 1f
            currentPlayer = null
        }
    }

    fun setLoudnessNormalizationEnabled(value: Boolean) {
        loudnessNormalizationEnabled = value
        prefs.edit().putBoolean(KEY_LOUDNESS_NORM_ENABLED, value).apply()
        if (!value) {
            // Same reasoning as detachFromPlayer above — turning this off
            // must actually undo its own effect, not just stop applying
            // new ones.
            currentPlayer?.volume = 1f
            runCatching { loudnessEnhancer?.setTargetGain(loudnessGainMillibels) }
            lastMeasuredLufs = null
            lastAppliedGainDb = null
            lastAnalyzedVideoPath = null
        }
    }

    /**
     * Analyzes ONE representative window of the current video's audio
     * (not the full track — decoding an entire feature film's audio
     * before playback could start would mean minutes of waiting, a real
     * cost this doesn't impose without being asked) and applies the
     * resulting gain. A stated v1 simplification: a single 60-second
     * window starting 3 minutes in (skipping any silent/logo intro most
     * films open with) rather than sampling multiple windows across the
     * runtime and averaging — a real, honest scope choice, not hidden.
     * Results aren't cached per video path yet either; re-opening the
     * same video re-analyzes rather than reusing a stored measurement —
     * a reasonable next enhancement, not built here.
     */
    suspend fun analyzeAndNormalizeLoudnessOnce(videoPath: String, videoDurationMs: Long) {
        if (lastAnalyzedVideoPath == videoPath) return
        analyzeAndNormalizeLoudness(videoPath, videoDurationMs)
        if (lastMeasuredLufs != null) lastAnalyzedVideoPath = videoPath
    }

    suspend fun analyzeAndNormalizeLoudness(videoPath: String, videoDurationMs: Long) {
        if (isAnalyzingLoudness) return
        isAnalyzingLoudness = true
        try {
            val windowDurationMs = 60_000L
            val startMs = if (videoDurationMs > 240_000L) 180_000L
                else (videoDurationMs / 2 - windowDurationMs / 2).coerceAtLeast(0L)
            val safeDuration = windowDurationMs.coerceAtMost((videoDurationMs - startMs).coerceAtLeast(1000L))

            val extracted = AutoSyncAudioExtractor.extractWindow(
                context = context,
                filePath = videoPath,
                trackLanguage = null,
                startMs = startMs,
                durationMs = safeDuration,
                targetSampleRate = LoudnessAnalyzer.analysisSampleRate()
            ) ?: run {
                isAnalyzingLoudness = false
                return
            }

            val result = withContext(kotlinx.coroutines.Dispatchers.Default) {
                LoudnessAnalyzer.analyze(extracted.samples, channelCount = 1)
            }

            if (result != null && result.integratedLufs.isFinite()) {
                lastMeasuredLufs = result.integratedLufs
                lastAppliedGainDb = result.gainDb
                applyLoudnessGain(result.gainDb)
            }
        } finally {
            isAnalyzingLoudness = false
        }
    }

    private fun applyLoudnessGain(gainDb: Double) {
        if (gainDb >= 0.0) {
            // Boost: LoudnessEnhancer, same effect this controller already
            // exposes as a manual slider — this just sets it
            // automatically instead of by hand. Doesn't touch the user's
            // own manually-set loudnessGainMillibels/prefs value; this is
            // a live application on top, matching how Adaptive Audio
            // never overwrites the user's own saved settings either.
            currentPlayer?.volume = 1f
            runCatching { loudnessEnhancer?.setTargetGain((gainDb * 100).toInt()) }
        } else {
            // Attenuate: Player.volume, the only tool here that can
            // reduce output below the source's own level.
            runCatching { loudnessEnhancer?.setTargetGain(0) }
            val linear = LoudnessAnalyzer.dbToLinear(gainDb).coerceIn(0f, 1f)
            currentPlayer?.volume = linear
        }
    }

    private fun attach(audioSessionId: Int) {
        if (audioSessionId == attachedSessionId && equalizer != null) return
        release()
        attachedSessionId = audioSessionId

        // Captured into a local BEFORE any .apply{} below: every one of
        // these effect classes (Equalizer/BassBoost/Virtualizer/
        // PresetReverb/LoudnessEnhancer) inherits AudioEffect.getEnabled(),
        // which Kotlin synthesizes into its own "enabled" property. Inside
        // an .apply{} block, that receiver-scoped "enabled" is CLOSER than
        // this controller's own "enabled" field of the same name, so a
        // bare `setEnabled(enabled)` call inside those blocks would
        // silently resolve to the effect's own freshly-constructed state
        // (false) instead of the user's actual persisted preference —
        // caught and fixed before shipping, not after.
        val shouldEnable = enabled

        // Priority 0 — this app doesn't need to outrank another app's
        // effects on the same session, which normally only happens with
        // system-level audio effect apps anyway.
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()?.apply {
            bandCount = numberOfBands
            val range = bandLevelRange
            bandLevelRangeDb = (range[0] / 100)..(range[1] / 100)
            bandCenterFreqHz = (0 until numberOfBands).map { getCenterFreq(it.toShort()) / 1000 }
            // Reconcile persisted levels with this device's actual band
            // count — a value saved on a device with a different band
            // count (or before any bands existed) shouldn't crash or
            // silently drop bands; pad/truncate to what's real here.
            val reconciled = (0 until numberOfBands).map { i -> bandLevelsDb.getOrElse(i) { 0 } }
            bandLevelsDb = reconciled
            setEnabled(shouldEnable)
            reconciled.forEachIndexed { index, db ->
                runCatching { setBandLevel(index.toShort(), (db * 100).toShort()) }
            }
        }

        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()?.apply {
            setEnabled(shouldEnable)
            runCatching { setStrength(bassBoostStrength.toShort()) }
        }

        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()?.apply {
            setEnabled(shouldEnable)
            runCatching { setStrength(virtualizerStrength.toShort()) }
        }

        presetReverb = runCatching { PresetReverb(0, audioSessionId) }.getOrNull()?.apply {
            setEnabled(shouldEnable)
            runCatching { preset = reverbPreset }
        }

        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()?.apply {
            setEnabled(shouldEnable)
            runCatching { setTargetGain(loudnessGainMillibels) }
        }

        if (dynamicsProcessingAvailable) {
            val dp = runCatching { DynamicsProcessing(0, audioSessionId) }.getOrNull()
            if (dp != null) {
                dp.setEnabled(shouldEnable)
                applyCompressorAmount(dp, compressorAmount)
                applyLimiterEnabled(dp, limiterEnabled)
            }
            dynamicsProcessing = dp
        }

        // Not wrapped in .apply{} deliberately — Visualizer also inherits
        // AudioEffect.getEnabled(), same receiver-scoping trap already
        // caught above; a plain local avoids it entirely rather than
        // relying on remembering to qualify every reference correctly.
        val vis = runCatching { Visualizer(audioSessionId) }.getOrNull()
        if (vis != null) {
            runCatching {
                val maxSize = Visualizer.getCaptureSizeRange()[1]
                vis.setCaptureSize(maxSize.coerceAtMost(1024))
                vis.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, capturedBytes: ByteArray?, samplingRate: Int) {
                            if (capturedBytes != null) {
                                waveform = capturedBytes
                                sceneAnalyzer.analyzeWaveform(capturedBytes)
                                onSceneAnalysisUpdated()
                            }
                        }
                        override fun onFftDataCapture(v: Visualizer?, capturedBytes: ByteArray?, samplingRate: Int) {
                            if (capturedBytes != null) {
                                fft = capturedBytes
                                // samplingRate arrives in milliHertz per
                                // Android's documented Visualizer contract
                                // — divided here before it's used for any
                                // bin-to-frequency math.
                                sceneAnalyzer.analyzeFft(capturedBytes, samplingRate / 1000)
                                onSceneAnalysisUpdated()
                            }
                        }
                    },
                    (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                    /* waveform = */ true,
                    /* fft = */ true
                )
                vis.setEnabled(shouldEnable)
            }
        }
        visualizer = vis
    }

    // Reads the device's actual default MBC band configuration (however
    // many bands that is — never assumed) and scales each band's
    // threshold/ratio between "no compression" (amount=0) and "noticeably
    // squeezed" (amount=100). Band indices are probed defensively rather
    // than trusting an assumed band-count getter, since getting that
    // wrong would silently skip real bands rather than crash — safer to
    // over-probe a few extra indices and let a failed one just fall
    // through runCatching.
    private fun applyCompressorAmount(dp: DynamicsProcessing, amount: Int) {
        val clamped = amount.coerceIn(0, 100)
        val fraction = clamped / 100f
        // 0dB (bypassed) down to -30dB at amount=100 — a musically
        // reasonable range, not an arbitrary number.
        val thresholdDb = -30f * fraction
        // 1:1 (no compression) up to 4:1 at amount=100 — gentle-to-
        // noticeable, not a brick-wall setting.
        val ratio = 1f + 3f * fraction
        for (band in 0..5) {
            runCatching {
                val mbcBand = dp.getMbcBandByChannelIndex(0, band) ?: return@runCatching
                mbcBand.threshold = thresholdDb
                mbcBand.ratio = ratio
                dp.setMbcBandByChannelIndex(0, band, mbcBand)
            }
        }
    }

    private fun applyLimiterEnabled(dp: DynamicsProcessing, isOn: Boolean) {
        runCatching {
            val limiter = dp.getLimiterByChannelIndex(0) ?: return@runCatching
            limiter.enabled = isOn
            dp.setLimiterByChannelIndex(0, limiter)
        }
    }

    fun setAdaptiveAudioEnabled(value: Boolean) {
        adaptiveAudioEnabled = value
        prefs.edit().putBoolean(KEY_ADAPTIVE_ENABLED, value).apply()
        if (!value) {
            restoreManualBaseline()
        }
    }

    // Called after every FFT/waveform capture — cheap (a handful of float
    // comparisons and, when adaptive mode is on, a small interpolation
    // step), never doing real work on every single capture beyond that.
    private fun onSceneAnalysisUpdated() {
        detectedScene = sceneAnalyzer.currentScene
        sceneDebugText = sceneAnalyzer.debugSnapshot()
        if (adaptiveAudioEnabled) {
            applyAdaptiveStep(detectedScene)
        }
    }

    // Target adjustments per scene — deltas/overrides layered on TOP of
    // the user's own manual baseline, not a replacement for it. Values
    // are a reasoned starting point (see AudioSceneAnalyzer.kt's own doc
    // comment on why these can't be verified without real content), kept
    // here as named locals rather than scattered magic numbers so they're
    // easy to find and retune together.
    private fun applyAdaptiveStep(scene: AudioScene) {
        val (targetVocalBoostDb, targetCompressorAmount, targetLimiterOn) = when (scene) {
            AudioScene.DIALOGUE -> Triple(4f, 35f, true)
            AudioScene.ACTION -> Triple(0f, 55f, true)
            AudioScene.MUSIC -> Triple(0f, 10f, false)
            // Neutral/unclear — ease back toward the user's own manual
            // baseline rather than holding onto whatever scene was
            // detected last.
            AudioScene.NEUTRAL -> Triple(0f, compressorAmount.toFloat(), limiterEnabled)
        }

        // Interpolate a fraction of the remaining distance each update
        // (~10Hz capture rate) rather than snapping — converges to the
        // target in well under a second, smoothly, with no audible jump
        // at a scene-change boundary.
        liveVocalBoostDb += INTERPOLATION_STEP * (targetVocalBoostDb - liveVocalBoostDb)
        liveCompressorAmount += INTERPOLATION_STEP * (targetCompressorAmount - liveCompressorAmount)

        applyLiveVocalBoost(liveVocalBoostDb.toInt())
        dynamicsProcessing?.let { dp ->
            applyCompressorAmount(dp, liveCompressorAmount.toInt().coerceIn(0, 100))
            applyLimiterEnabled(dp, targetLimiterOn)
        }
    }

    // Applies a temporary delta directly to whichever Equalizer bands
    // fall in the vocal-presence range, on top of the user's own
    // persisted band levels — NEVER writes to bandLevelsDb or
    // SharedPreferences. This is the one place in this whole controller
    // that intentionally bypasses the normal setBandLevel() path, exactly
    // because this adjustment must stay invisible to persistence.
    private fun applyLiveVocalBoost(deltaDb: Int) {
        val eq = equalizer ?: return
        bandCenterFreqHz.forEachIndexed { index, freqHz ->
            if (freqHz in AudioSceneAnalyzer.VOCAL_BAND_LOW_HZ..AudioSceneAnalyzer.VOCAL_BAND_HIGH_HZ) {
                val baseline = bandLevelsDb.getOrElse(index) { 0 }
                val adjusted = (baseline + deltaDb).coerceIn(bandLevelRangeDb.first, bandLevelRangeDb.last)
                runCatching { eq.setBandLevel(index.toShort(), (adjusted * 100).toShort()) }
            }
        }
    }

    // Undoes any live adaptive overlay by re-applying the user's actual
    // persisted values through the same private helpers used at attach()
    // time — the effects end up in exactly the state they'd be in if
    // adaptive mode had never touched them.
    private fun restoreManualBaseline() {
        liveVocalBoostDb = 0f
        liveCompressorAmount = compressorAmount.toFloat()
        applyLiveVocalBoost(0)
        dynamicsProcessing?.let { dp ->
            applyCompressorAmount(dp, compressorAmount)
            applyLimiterEnabled(dp, limiterEnabled)
        }
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        presetReverb?.release(); presetReverb = null
        loudnessEnhancer?.release(); loudnessEnhancer = null
        dynamicsProcessing?.release(); dynamicsProcessing = null
        visualizer?.release(); visualizer = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        equalizer?.let { runCatching { it.enabled = value } }
        bassBoost?.let { runCatching { it.enabled = value } }
        virtualizer?.let { runCatching { it.enabled = value } }
        presetReverb?.let { runCatching { it.enabled = value } }
        loudnessEnhancer?.let { runCatching { it.enabled = value } }
        dynamicsProcessing?.let { dp -> runCatching { dp.enabled = value } }
        visualizer?.let { v -> runCatching { v.enabled = value } }
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setBandLevel(bandIndex: Int, db: Int) {
        val clamped = db.coerceIn(bandLevelRangeDb.first, bandLevelRangeDb.last)
        val updated = bandLevelsDb.toMutableList()
        if (bandIndex !in updated.indices) return
        updated[bandIndex] = clamped
        bandLevelsDb = updated
        runCatching { equalizer?.setBandLevel(bandIndex.toShort(), (clamped * 100).toShort()) }
        saveBandLevels(updated)
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        bassBoostStrength = clamped
        runCatching { bassBoost?.setStrength(clamped.toShort()) }
        prefs.edit().putInt(KEY_BASS_STRENGTH, clamped).apply()
    }

    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        virtualizerStrength = clamped
        runCatching { virtualizer?.setStrength(clamped.toShort()) }
        prefs.edit().putInt(KEY_VIRTUALIZER_STRENGTH, clamped).apply()
    }

    fun setReverbPreset(preset: Short) {
        reverbPreset = preset
        runCatching { presetReverb?.preset = preset }
        prefs.edit().putInt(KEY_REVERB_PRESET, preset.toInt()).apply()
    }

    fun setLoudnessGain(millibels: Int) {
        // LoudnessEnhancer's useful range is device/content dependent —
        // Android doesn't expose a queryable min/max the way Equalizer
        // does. 0–2000mB (0–20dB) is the commonly used safe UI range in
        // apps that expose this effect; clamped here rather than trusting
        // whatever the caller passes.
        val clamped = millibels.coerceIn(0, 2000)
        loudnessGainMillibels = clamped
        runCatching { loudnessEnhancer?.setTargetGain(clamped) }
        prefs.edit().putInt(KEY_LOUDNESS_MB, clamped).apply()
    }

    fun setCompressorAmount(amount: Int) {
        val clamped = amount.coerceIn(0, 100)
        compressorAmount = clamped
        dynamicsProcessing?.let { applyCompressorAmount(it, clamped) }
        prefs.edit().putInt(KEY_COMPRESSOR_AMOUNT, clamped).apply()
    }

    fun setLimiterEnabled(isOn: Boolean) {
        limiterEnabled = isOn
        dynamicsProcessing?.let { applyLimiterEnabled(it, isOn) }
        prefs.edit().putBoolean(KEY_LIMITER_ENABLED, isOn).apply()
    }

    fun resetAll() {
        setAdaptiveAudioEnabled(false)
        setLoudnessNormalizationEnabled(false)
        setEnabled(false)
        bandLevelsDb.indices.forEach { setBandLevel(it, 0) }
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
        setReverbPreset(PresetReverb.PRESET_NONE)
        setLoudnessGain(0)
        setCompressorAmount(0)
        setLimiterEnabled(false)
    }

    /**
     * Captures every current value into a named preset. Overwrites any
     * existing preset with the same name (so re-saving under the same
     * name updates it, matching how "save" is expected to behave
     * everywhere else) rather than creating a duplicate.
     */
    fun saveCurrentAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val preset = AudioFxPreset(
            name = trimmed,
            enabled = enabled,
            bandLevelsDb = bandLevelsDb,
            bassBoostStrength = bassBoostStrength,
            virtualizerStrength = virtualizerStrength,
            reverbPreset = reverbPreset,
            loudnessGainMillibels = loudnessGainMillibels,
            compressorAmount = compressorAmount,
            limiterEnabled = limiterEnabled
        )
        val updated = presets.filterNot { it.name == trimmed } + preset
        presets = updated
        savePresets(updated)
    }

    /**
     * Applies every value from a saved preset via the SAME individual
     * setters used everywhere else in this controller — reuses all
     * existing clamping/persistence/effect-push logic rather than
     * duplicating it, so a loaded preset is indistinguishable from
     * someone manually setting each slider to those values.
     */
    fun applyPreset(name: String) {
        val preset = presets.firstOrNull { it.name == name } ?: return
        setEnabled(preset.enabled)
        preset.bandLevelsDb.forEachIndexed { index, db -> setBandLevel(index, db) }
        setBassBoostStrength(preset.bassBoostStrength)
        setVirtualizerStrength(preset.virtualizerStrength)
        setReverbPreset(preset.reverbPreset)
        setLoudnessGain(preset.loudnessGainMillibels)
        setCompressorAmount(preset.compressorAmount)
        setLimiterEnabled(preset.limiterEnabled)
    }

    fun deletePreset(name: String) {
        val updated = presets.filterNot { it.name == name }
        presets = updated
        savePresets(updated)
        // A deleted preset can't stay assigned to an output profile —
        // clear any assignment pointing at it rather than leaving a
        // dangling reference that silently does nothing next time that
        // output connects.
        if (profileAssignments.containsValue(name)) {
            val updatedAssignments = profileAssignments.filterValues { it != name }
            profileAssignments = updatedAssignments
            saveProfileAssignments(updatedAssignments)
        }
    }

    fun assignPresetToProfile(profile: AudioOutputProfile, presetName: String?) {
        val updated = if (presetName == null) {
            profileAssignments - profile
        } else {
            profileAssignments + (profile to presetName)
        }
        profileAssignments = updated
        saveProfileAssignments(updated)
    }

    private fun loadEnabled(key: String = KEY_ENABLED): Boolean = prefs.getBoolean(key, false)

    private fun loadIntPref(key: String, default: Int): Int = prefs.getInt(key, default)

    private fun loadBandLevels(): List<Int> {
        val raw = prefs.getString(KEY_BAND_LEVELS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getInt(it) }
        }.getOrDefault(emptyList())
    }

    private fun saveBandLevels(levels: List<Int>) {
        val array = JSONArray()
        levels.forEach { array.put(it) }
        prefs.edit().putString(KEY_BAND_LEVELS, array.toString()).apply()
    }

    private fun loadPresets(): List<AudioFxPreset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { AudioFxPreset.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun savePresets(list: List<AudioFxPreset>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun loadProfileAssignments(): Map<AudioOutputProfile, String> {
        val raw = prefs.getString(KEY_PROFILE_ASSIGNMENTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            AudioOutputProfile.entries.mapNotNull { profile ->
                val presetName = obj.optString(profile.name, "").ifBlank { null }
                presetName?.let { profile to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveProfileAssignments(map: Map<AudioOutputProfile, String>) {
        val obj = JSONObject()
        map.forEach { (profile, presetName) -> obj.put(profile.name, presetName) }
        prefs.edit().putString(KEY_PROFILE_ASSIGNMENTS, obj.toString()).apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BAND_LEVELS = "band_levels_db"
        private const val KEY_BASS_STRENGTH = "bass_strength"
        private const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
        private const val KEY_REVERB_PRESET = "reverb_preset"
        private const val KEY_LOUDNESS_MB = "loudness_millibels"
        private const val KEY_COMPRESSOR_AMOUNT = "compressor_amount"
        private const val KEY_LIMITER_ENABLED = "limiter_enabled"
        private const val KEY_LOUDNESS_NORM_ENABLED = "loudness_normalization_enabled"
        private const val KEY_PRESETS = "presets"
        private const val KEY_PROFILE_ASSIGNMENTS = "profile_assignments"
        private const val KEY_ADAPTIVE_ENABLED = "adaptive_audio_enabled"

        // Fraction of the remaining distance to the scene target covered
        // per analysis update (~10Hz) — 0.2 converges in well under a
        // second. Higher = snappier scene response but more audible
        // transition; lower = smoother but slower to react. A reasonable
        // starting point, same tuning caveat as everything in
        // AudioSceneAnalyzer.kt.
        private const val INTERPOLATION_STEP = 0.2f

        // Standard Android PresetReverb values (android.media.audiofx.
        // PresetReverb) — named here so the dashboard doesn't need its own
        // copy of these constants.
        val REVERB_PRESETS: List<Pair<Short, String>> = listOf(
            PresetReverb.PRESET_NONE to "None",
            PresetReverb.PRESET_SMALLROOM to "Small Room",
            PresetReverb.PRESET_MEDIUMROOM to "Medium Room",
            PresetReverb.PRESET_LARGEROOM to "Large Room",
            PresetReverb.PRESET_MEDIUMHALL to "Medium Hall",
            PresetReverb.PRESET_LARGEHALL to "Large Hall",
            PresetReverb.PRESET_PLATE to "Plate"
        )
    }
}
