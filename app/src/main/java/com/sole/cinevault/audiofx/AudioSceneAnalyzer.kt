package com.sole.cinevault.audiofx

import kotlin.math.abs
import kotlin.math.sqrt

internal enum class AudioScene(val label: String) {
    DIALOGUE("Dialogue"),
    ACTION("Action"),
    MUSIC("Music"),
    NEUTRAL("Neutral")
}

/**
 * AudioSceneAnalyzer.kt
 *
 * A real-time heuristic classifier — NOT a trained ML model, and worth
 * being direct about the difference: this analyzes genuine, real audio
 * features (spectral energy distribution, transient/dynamics behavior)
 * using signal-processing math that can be checked line by line, but the
 * THRESHOLDS below are an informed starting point based on how these
 * characteristics generally behave in film/TV audio, not values verified
 * against real content. That verification genuinely cannot be done here —
 * it needs a real device playing real movies while someone listens and
 * checks whether the detected scene matches what's actually happening on
 * screen. This is fundamentally different from every other piece of this
 * Audio Studio work: a compressor setter either works or it doesn't; a
 * classifier can be "technically working exactly as written" and still be
 * WRONG about what's happening in a given scene. Treat the constants below
 * as the first thing to retune after real-world testing, not as settled.
 *
 * Features computed, and why:
 *   - Vocal-band energy ratio: how much of the total spectral energy
 *     falls in 300Hz-3400Hz (the classic telephone-bandwidth definition
 *     of the range that carries speech intelligibility — NOT the same as
 *     a voice's fundamental pitch, which sits lower). Dialogue
 *     concentrates energy here; explosions and sub-bass-heavy music don't.
 *   - Low-band energy ratio: energy below 150Hz. Explosions, impacts,
 *     engine rumble, and music low-end live here; real dialogue has very
 *     little.
 *   - Crest factor (peak / RMS from the waveform capture): a measure of
 *     "spikiness." Action-scene transients (gunshots, explosions) produce
 *     a high crest factor — a sharp peak well above the average level.
 *     Heavily-compressed dialogue and much modern music sit lower.
 *
 * Frequency-per-FFT-bin math is the one part of this file that HAD to be
 * verified against Android's documented Visualizer contract before
 * writing anything — getting it wrong would mean silently analyzing the
 * wrong frequency range with no crash and no visible error. Per Android's
 * own docs: samplingRate arrives in milliHertz, and for capture size n,
 * the kth frequency point is (k * Fs) / (n/2), where k=0 is DC and
 * k=n/2 is Nyquist. See the comment inline below for how that maps onto
 * this class's own bin-indexing (which skips the DC/Nyquist-only first
 * two bytes, same as the dashboard's own spectrum visualizer).
 */
internal class AudioSceneAnalyzer {
    private var smoothedVocalRatio = 0f
    private var smoothedLowRatio = 0f
    private var smoothedCrestFactor = 1f
    private var hasFirstFftSample = false
    private var hasFirstWaveformSample = false

    private var candidateScene = AudioScene.NEUTRAL
    private var candidateStreak = 0

    var currentScene: AudioScene = AudioScene.NEUTRAL
        private set

    fun analyzeFft(fft: ByteArray, samplingRateHz: Int) {
        if (fft.size < 4 || samplingRateHz <= 0) return
        val binCount = (fft.size / 2) - 1
        if (binCount <= 0) return

        var totalEnergy = 0f
        var vocalEnergy = 0f
        var lowEnergy = 0f

        for (bin in 0 until binCount) {
            val reIndex = (bin + 1) * 2
            val imIndex = reIndex + 1
            if (imIndex >= fft.size) break
            val re = fft[reIndex].toInt()
            val im = fft[imIndex].toInt()
            val magnitude = sqrt((re * re + im * im).toFloat())
            // Point index here is bin+1 — bin 0 in this loop is the first
            // real/imaginary PAIR, which comes after the DC-only and
            // Nyquist-only first two bytes of the capture (same indexing
            // the dashboard's SpectrumVisualizer already uses).
            val freqHz = ((bin + 1).toFloat() * samplingRateHz) / (fft.size / 2f)
            totalEnergy += magnitude
            if (freqHz in VOCAL_BAND_LOW_HZ.toFloat()..VOCAL_BAND_HIGH_HZ.toFloat()) {
                vocalEnergy += magnitude
            }
            if (freqHz < LOW_BAND_HIGH_HZ) {
                lowEnergy += magnitude
            }
        }

        // Near-silence — a quiet pause between lines, a fade — shouldn't
        // be classified as anything; the ratios would be noise-floor
        // garbage, not a meaningful reading.
        if (totalEnergy < MIN_ENERGY_TO_CLASSIFY) return

        val vocalRatio = vocalEnergy / totalEnergy
        val lowRatio = lowEnergy / totalEnergy
        if (!hasFirstFftSample) {
            smoothedVocalRatio = vocalRatio
            smoothedLowRatio = lowRatio
            hasFirstFftSample = true
        } else {
            smoothedVocalRatio += EMA_ALPHA * (vocalRatio - smoothedVocalRatio)
            smoothedLowRatio += EMA_ALPHA * (lowRatio - smoothedLowRatio)
        }
        reclassify()
    }

    fun analyzeWaveform(waveform: ByteArray) {
        if (waveform.size < 8) return
        // Waveform capture is unsigned PCM8 centered at 128 — recenter to
        // get a magnitude around 0 before computing peak/RMS.
        var peak = 0f
        var sumSquares = 0.0
        for (b in waveform) {
            val sample = abs((b.toInt() and 0xFF) - 128).toFloat()
            if (sample > peak) peak = sample
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / waveform.size).toFloat()
        if (rms < MIN_RMS_TO_CLASSIFY) return // near-silence, same reasoning as the FFT side
        val crestFactor = peak / rms
        if (!hasFirstWaveformSample) {
            smoothedCrestFactor = crestFactor
            hasFirstWaveformSample = true
        } else {
            smoothedCrestFactor += EMA_ALPHA * (crestFactor - smoothedCrestFactor)
        }
        reclassify()
    }

    private fun reclassify() {
        val raw = when {
            smoothedVocalRatio >= DIALOGUE_VOCAL_RATIO_MIN && smoothedLowRatio <= DIALOGUE_LOW_RATIO_MAX ->
                AudioScene.DIALOGUE
            smoothedLowRatio >= ACTION_LOW_RATIO_MIN && smoothedCrestFactor >= ACTION_CREST_FACTOR_MIN ->
                AudioScene.ACTION
            smoothedLowRatio in MUSIC_LOW_RATIO_RANGE && smoothedVocalRatio < DIALOGUE_VOCAL_RATIO_MIN ->
                AudioScene.MUSIC
            else -> AudioScene.NEUTRAL
        }

        // Hysteresis: a single noisy reading shouldn't flip the detected
        // scene — require several consecutive updates agreeing before the
        // reported scene actually changes, so a brief quiet moment during
        // an action scene (or a loud line of dialogue) doesn't cause
        // rapid flickering between classifications.
        if (raw == candidateScene) {
            candidateStreak++
        } else {
            candidateScene = raw
            candidateStreak = 1
        }
        if (candidateStreak >= HYSTERESIS_STREAK_REQUIRED) {
            currentScene = candidateScene
        }
    }

    // For a debug readout in the dashboard — without visibility into the
    // actual feature values, "why did it think this was Action" is
    // unanswerable, and retuning the thresholds after real-world testing
    // would be pure guesswork rather than informed adjustment.
    fun debugSnapshot(): String =
        "vocal %.0f%% · low %.0f%% · crest %.1f".format(smoothedVocalRatio * 100, smoothedLowRatio * 100, smoothedCrestFactor)

    companion object {
        private const val EMA_ALPHA = 0.15f
        // Visualizer's capture callback fires at whatever rate was passed
        // to setDataCaptureListener() — this controller uses roughly
        // Visualizer.getMaxCaptureRate()/2, commonly in the ~10-20Hz
        // range on real devices. At ~10Hz, 6 consecutive matching
        // readings is roughly 0.6s of sustained agreement before
        // switching — long enough to ignore a brief blip, short enough
        // that a genuine scene change doesn't feel sluggish. Worth
        // retuning alongside the classification thresholds themselves.
        private const val HYSTERESIS_STREAK_REQUIRED = 6

        const val VOCAL_BAND_LOW_HZ = 300
        const val VOCAL_BAND_HIGH_HZ = 3400
        const val LOW_BAND_HIGH_HZ = 150

        private const val MIN_ENERGY_TO_CLASSIFY = 1f
        private const val MIN_RMS_TO_CLASSIFY = 1f

        private const val DIALOGUE_VOCAL_RATIO_MIN = 0.40f
        private const val DIALOGUE_LOW_RATIO_MAX = 0.15f
        private const val ACTION_LOW_RATIO_MIN = 0.25f
        private const val ACTION_CREST_FACTOR_MIN = 3.5f
        private val MUSIC_LOW_RATIO_RANGE = 0.10f..0.30f
    }
}
