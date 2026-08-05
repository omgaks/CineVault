package com.sole.cinevault.subtitles

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ── Auto-Sync engine — Phase 2: multi-window drift-aware alignment ──────
// Phase 1 analyzed a single window near the start and could only ever
// return a flat offset. Phase 2 samples up to three independent windows
// across the film — early, middle, late — and:
//   - If all windows agree on roughly the same offset -> flat correction,
//     same as before, but confidence is now backed by real cross-window
//     agreement instead of just one window's internal peak sharpness.
//   - If the offset changes roughly LINEARLY across all three windows
//     (the classic 23.976/25fps frame-rate-mismatch signature) -> fits a
//     genuine drift correction (timeScale != 1.0), reusing the same
//     linear-fit convention (corrected = original * scale + offset) the
//     manual "Fix Gradual Drift" tool already uses.
//   - Drift is ONLY ever fitted with all three windows, never two — any
//     two points trivially form a "perfect" line with zero residual by
//     definition, which would make two disagreeing windows look falsely
//     confident. A third, independent point is what actually lets the
//     fit be checked, not just drawn.
//   - Anything that's neither flat nor a validated line refuses to guess
//     — returns "could not determine reliable sync" rather than averaging
//     together numbers that don't actually agree. That pattern usually
//     means a different cut/edit of the film, not a fixable offset.
//
// Each window's own scoring also changed: previously only checked "of the
// subtitle-active moments, how many overlap real speech" (precision) —
// that never penalized long stretches of unexplained real speech the
// subtitle has no cue for at all. The search below now maximizes a real
// F1 score (precision AND recall together).
enum class SyncMethod { SPEECH_TIMING, SPEECH_TEXT_MATCH, CUE_PATTERN, HYBRID }

data class SyncAnchor(val subtitleTimeMs: Long, val audioTimeMs: Long)

data class SubtitleSyncResult(
    val initialOffsetMs: Long,
    val timeScale: Double,
    val anchors: List<SyncAnchor>,
    val confidence: Float,
    val method: SyncMethod,
    val averageErrorMs: Long
)

sealed class AutoSyncStatus {
    object Idle : AutoSyncStatus()
    data class Analyzing(val stage: String) : AutoSyncStatus()
    data class Success(val result: SubtitleSyncResult) : AutoSyncStatus()
    data class LowConfidence(val result: SubtitleSyncResult) : AutoSyncStatus()
    data class Failed(val reason: String) : AutoSyncStatus()
}

object AutoSyncEngine {

    // FIX: was 6 minutes per window (up to 3 windows) — a 6-minute window
    // of source audio, held as raw stereo PCM + downmixed mono + resampled
    // copies simultaneously during extraction, could peak well over 100MB
    // on its own, which combined with the app's other in-memory caches
    // (thumbnail/preview LruCaches, ExoPlayer's own buffers) was enough to
    // blow a 256MB heap. Dropped to 45s/30s — small enough that even the
    // least memory-conscious path through the extractor stays well clear
    // of the ceiling, while still giving speech-timing correlation enough
    // material to work with (a 45s window still spans many lines of
    // dialogue in virtually all content).
    private const val WINDOW_DURATION_MS = 45_000L
    private const val MIN_WINDOW_DURATION_MS = 30_000L

    // Matches the manual Sync slider's own -10s..+10s range — Auto-Sync
    // can't produce a flat offset the manual controls couldn't represent.
    private const val MAX_OFFSET_MS = 10_000L
    private const val SEARCH_STEP_MS = 40L
    private const val TIMELINE_STEP_MS = 20L

    // Two offsets are considered "the same" (flat correction, not drift)
    // when they're within this of each other.
    private const val AGREEMENT_TOLERANCE_MS = 150L

    // After fitting a drift correction through the early/late windows, the
    // MIDDLE window's actual offset must land within this of what the fit
    // predicts — otherwise the "drift" isn't real, it's noise or a
    // structurally different cut of the film.
    private const val MAX_DRIFT_RESIDUAL_MS = 300L

    // Real frame-rate mismatches (23.976<->25, 24<->25, etc.) land within
    // roughly +-5%. A fitted scale outside this range almost certainly
    // means the three windows don't actually describe consistent drift —
    // reject rather than apply an implausible stretch factor.
    private val PLAUSIBLE_SCALE_RANGE = 0.90..1.10

    private val TIMING_REGEX = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

    private data class WindowResult(
        val windowCenterMs: Long,
        val offsetMs: Long,
        val bestF1: Double,
        val secondBestF1: Double,
        val subtitleCoverage: Float
    )

    // FIX: now takes videoDurationMs — multi-window placement (early/mid/
    // late) genuinely needs to know how long the film is to place windows
    // meaningfully. Passed in from VideoPlayerScreen.kt's exoPlayer.duration.
    suspend fun run(
        context: Context,
        videoPath: String,
        videoDurationMs: Long,
        audioTrackLanguage: String?,
        subtitleSrtText: String
    ): AutoSyncStatus {
        if (videoPath.startsWith("smb://", ignoreCase = true)) {
            return AutoSyncStatus.Failed("Auto-Sync isn't available for network shares yet — try Dialogue Sync or the manual slider instead.")
        }
        val isContentUri = videoPath.startsWith("content://", ignoreCase = true)
        if (!isContentUri && !java.io.File(videoPath).exists()) {
            return AutoSyncStatus.Failed("Couldn't read this video file for analysis.")
        }

        val windows = computeWindows(videoDurationMs)
        val results = mutableListOf<WindowResult>()
        for ((startMs, durationMs) in windows) {
            currentCoroutineContext().ensureActive()
            analyzeWindow(context, videoPath, audioTrackLanguage, subtitleSrtText, startMs, durationMs)?.let { results.add(it) }
        }

        if (results.isEmpty()) {
            return AutoSyncStatus.Failed("Couldn't find enough matching speech and subtitle timing anywhere in the sampled portions of this video.")
        }

        if (results.size == 1) {
            val only = results.first()
            val confidence = singleWindowConfidence(only)
            val result = SubtitleSyncResult(
                initialOffsetMs = only.offsetMs,
                timeScale = 1.0,
                anchors = listOf(SyncAnchor(only.windowCenterMs, only.windowCenterMs + only.offsetMs)),
                confidence = confidence,
                method = SyncMethod.SPEECH_TIMING,
                averageErrorMs = 0L
            )
            return if (confidence >= 0.6f) AutoSyncStatus.Success(result) else AutoSyncStatus.LowConfidence(result)
        }

        val offsets = results.map { it.offsetMs }
        val spread = offsets.max() - offsets.min()

        if (spread <= AGREEMENT_TOLERANCE_MS) {
            // Flat correction — weight each window's contribution by its
            // own F1 score, so a window with a cleaner match pulls the
            // averaged offset more than a shakier one.
            val totalWeight = results.sumOf { it.bestF1 }.coerceAtLeast(0.01)
            val weightedOffset = results.sumOf { it.offsetMs * it.bestF1 } / totalWeight
            val avgF1 = results.map { it.bestF1 }.average()
            val confidence = multiWindowFlatConfidence(avgF1, results.size, spread)
            val result = SubtitleSyncResult(
                initialOffsetMs = weightedOffset.toLong(),
                timeScale = 1.0,
                anchors = results.map { SyncAnchor(it.windowCenterMs, it.windowCenterMs + it.offsetMs) },
                confidence = confidence,
                method = SyncMethod.SPEECH_TIMING,
                averageErrorMs = spread
            )
            return if (confidence >= 0.6f) AutoSyncStatus.Success(result) else AutoSyncStatus.LowConfidence(result)
        }

        if (results.size < 3) {
            // Two windows that disagree — a line can always be drawn
            // through exactly two points, but that's not evidence of real
            // drift without a third, independent point to check it
            // against. Refuse rather than fit a falsely-confident line.
            return AutoSyncStatus.Failed("Timing looks inconsistent between the two sampled sections of this film — try again, or use Dialogue Sync for the part you're watching.")
        }

        // Three windows, not flat — check whether it's a consistent
        // linear drift. Fit through the two most separated (first/last by
        // position) windows, then validate against the middle one.
        val sortedByPosition = results.sortedBy { it.windowCenterMs }
        val first = sortedByPosition.first()
        val middle = sortedByPosition[1]
        val last = sortedByPosition.last()

        if (first.windowCenterMs == last.windowCenterMs) {
            return AutoSyncStatus.Failed("Could not determine reliable sync — timing looks inconsistent across the film.")
        }

        // corrected = a * original + b, solved through the two endpoints:
        // (first.windowCenterMs, first.windowCenterMs + first.offsetMs)
        // and (last.windowCenterMs, last.windowCenterMs + last.offsetMs).
        // This is the exact same convention shiftSrtTimestampMatch() in
        // VideoPlayerScreen.kt already applies (originalMs * scale +
        // offsetMs), so a fitted result here composes directly with it.
        val x1 = first.windowCenterMs.toDouble(); val y1 = (first.windowCenterMs + first.offsetMs).toDouble()
        val x2 = last.windowCenterMs.toDouble(); val y2 = (last.windowCenterMs + last.offsetMs).toDouble()
        val a = (y2 - y1) / (x2 - x1)
        val b = y1 - a * x1

        if (a !in PLAUSIBLE_SCALE_RANGE) {
            return AutoSyncStatus.Failed("Timing looks inconsistent across the film rather than a steady drift — this subtitle may be for a different cut or edit. Try Dialogue Sync for the section you're watching instead.")
        }

        val predictedMiddle = a * middle.windowCenterMs + b
        val actualMiddle = (middle.windowCenterMs + middle.offsetMs).toDouble()
        val residual = abs(predictedMiddle - actualMiddle)

        if (residual > MAX_DRIFT_RESIDUAL_MS) {
            return AutoSyncStatus.Failed("Timing looks inconsistent across the film rather than a steady drift — this subtitle may be for a different cut or edit. Try Dialogue Sync for the section you're watching instead.")
        }

        val avgF1 = results.map { it.bestF1 }.average()
        val confidence = driftConfidence(avgF1, residual)
        val result = SubtitleSyncResult(
            initialOffsetMs = b.toLong().coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS),
            timeScale = a,
            anchors = results.map { SyncAnchor(it.windowCenterMs, it.windowCenterMs + it.offsetMs) },
            confidence = confidence,
            method = SyncMethod.SPEECH_TIMING,
            averageErrorMs = residual.toLong()
        )
        return if (confidence >= 0.6f) AutoSyncStatus.Success(result) else AutoSyncStatus.LowConfidence(result)
    }

    // Places up to three windows (early ~10%, middle ~48%, late ~82%)
    // across the film, each clamped to fit inside its actual duration.
    // Degrades gracefully for short content: under ~8 minutes total,
    // there isn't room for three genuinely separated windows, so this
    // falls back to a single window covering most of the video instead of
    // three overlapping/degenerate ones.
    private fun computeWindows(videoDurationMs: Long): List<Pair<Long, Long>> {
        if (videoDurationMs <= 0L) {
            // Unknown duration (shouldn't normally happen once ExoPlayer
            // has prepared the media, but guarded rather than crash) —
            // fall back to a single Phase-1-style window near the start.
            return listOf(60_000L to WINDOW_DURATION_MS)
        }

        if (videoDurationMs < 8 * 60_000L) {
            val start = (videoDurationMs * 0.1).toLong().coerceAtLeast(5_000L).coerceAtMost((videoDurationMs - 10_000L).coerceAtLeast(0L))
            val duration = (videoDurationMs - start).coerceAtMost(WINDOW_DURATION_MS)
            return if (duration >= 10_000L) listOf(start to duration) else emptyList()
        }

        val windowDuration = WINDOW_DURATION_MS.coerceAtMost(videoDurationMs / 6).coerceAtLeast(MIN_WINDOW_DURATION_MS)
        fun clampedStart(fraction: Double) = (videoDurationMs * fraction).toLong().coerceIn(0L, (videoDurationMs - windowDuration).coerceAtLeast(0L))

        val earlyStart = max(60_000L, clampedStart(0.10))
        val midStart = clampedStart(0.48)
        val lateStart = clampedStart(0.82)

        val windows = mutableListOf<Pair<Long, Long>>()
        windows.add(earlyStart to windowDuration)
        // Only add mid/late if they're genuinely separated from the
        // previous window — on shorter videos the fraction-based starts
        // can land close enough together that a "second window" would
        // just be re-analyzing almost the same audio.
        if (midStart > earlyStart + windowDuration / 2) windows.add(midStart to windowDuration)
        if (lateStart > (windows.last().first) + windowDuration / 2) windows.add(lateStart to windowDuration)

        return windows
    }

    private suspend fun analyzeWindow(
        context: Context,
        videoPath: String,
        audioTrackLanguage: String?,
        subtitleSrtText: String,
        windowStartMs: Long,
        windowDurationMs: Long
    ): WindowResult? {
        val extracted = AutoSyncAudioExtractor.extractWindow(
            context = context,
            filePath = videoPath,
            trackLanguage = audioTrackLanguage,
            startMs = windowStartMs,
            durationMs = windowDurationMs
        ) ?: return null
        if (extracted.samples.isEmpty()) return null

        val vadTimeline = try {
            buildVadTimeline(context, extracted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (vadTimeline.none { it }) return null

        val subtitleTimeline = buildSubtitleTimeline(subtitleSrtText, windowStartMs, windowDurationMs)
        if (subtitleTimeline.none { it }) return null

        val (bestOffsetMs, bestF1, secondBestF1) = searchBestOffset(subtitleTimeline, vadTimeline)
        val coverage = subtitleTimeline.count { it }.toFloat() / subtitleTimeline.size.coerceAtLeast(1)
        val windowCenterMs = windowStartMs + windowDurationMs / 2

        return WindowResult(windowCenterMs, bestOffsetMs, bestF1, secondBestF1, coverage)
    }

    private suspend fun buildVadTimeline(context: Context, audio: AutoSyncAudioExtractor.ExtractedAudio): BooleanArray {
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.25f,
                windowSize = 512
            ),
            sampleRate = audio.sampleRate,
            numThreads = 1,
            provider = "cpu"
        )
        val vad = Vad(assetManager = context.assets, config = config)
        try {
            val stepSamples = (audio.sampleRate * TIMELINE_STEP_MS / 1000L).toInt().coerceAtLeast(1)
            val windowSize = config.sileroVadModelConfig.windowSize
            val totalSteps = audio.samples.size / stepSamples
            val timeline = BooleanArray(totalSteps)

            var sampleIdx = 0
            var stepIdx = 0
            var stepAccumulatorActive = false
            var samplesInCurrentStep = 0

            while (sampleIdx + windowSize <= audio.samples.size && stepIdx < totalSteps) {
                currentCoroutineContext().ensureActive()
                val chunk = audio.samples.copyOfRange(sampleIdx, sampleIdx + windowSize)
                val prob = vad.compute(chunk)
                if (prob >= config.sileroVadModelConfig.threshold) stepAccumulatorActive = true
                samplesInCurrentStep += windowSize
                sampleIdx += windowSize

                if (samplesInCurrentStep >= stepSamples) {
                    timeline[stepIdx] = stepAccumulatorActive
                    stepIdx++
                    samplesInCurrentStep = 0
                    stepAccumulatorActive = false
                }
            }
            return timeline
        } finally {
            vad.release()
        }
    }

    private fun buildSubtitleTimeline(srtText: String, windowStartMs: Long, windowDurationMs: Long): BooleanArray {
        val steps = (windowDurationMs / TIMELINE_STEP_MS).toInt()
        val timeline = BooleanArray(steps)
        val blocks = parseSrtBlocks(srtText)
        val windowEndMs = windowStartMs + windowDurationMs

        for (block in blocks) {
            val range = parseTimingRangeMs(block.timing) ?: continue
            val (cueStart, cueEnd) = range
            if (cueEnd < windowStartMs || cueStart > windowEndMs) continue
            val clampedStart = max(cueStart, windowStartMs)
            val clampedEnd = min(cueEnd, windowEndMs)
            val startStep = ((clampedStart - windowStartMs) / TIMELINE_STEP_MS).toInt().coerceIn(0, steps)
            val endStep = ((clampedEnd - windowStartMs) / TIMELINE_STEP_MS).toInt().coerceIn(0, steps)
            for (i in startStep until endStep) timeline[i] = true
        }
        return timeline
    }

    private fun parseTimingRangeMs(timing: String): Pair<Long, Long>? {
        val m = TIMING_REGEX.find(timing) ?: return null
        fun toMs(h: String, mi: String, s: String, ms: String) =
            h.toLong() * 3_600_000L + mi.toLong() * 60_000L + s.toLong() * 1_000L + ms.toLong()
        val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
        val end = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
        return start to end
    }

    // Returns (bestOffsetMs, bestF1, secondBestF1). F1 = 2*precision*recall
    // / (precision+recall), where precision is "of the subtitle-active
    // moments, how many have real speech" (the ONLY signal the old scoring
    // used) and recall is "of the moments with real speech, how many the
    // subtitle actually covers" — the piece that was missing, and exactly
    // what let a subtitle with sparse/wrong cues score well before as long
    // as its few cues happened to land on real speech, without ever being
    // penalized for ignoring long stretches of actual dialogue.
    private fun searchBestOffset(subtitleTimeline: BooleanArray, vadTimeline: BooleanArray): Triple<Long, Double, Double> {
        val maxOffsetSteps = (MAX_OFFSET_MS / TIMELINE_STEP_MS).toInt()
        val searchStepInSteps = (SEARCH_STEP_MS / TIMELINE_STEP_MS).toInt().coerceAtLeast(1)
        val vadActiveCount = vadTimeline.count { it }

        fun scoreAt(offsetSteps: Int): Double {
            var truePositive = 0
            var subtitleActiveCount = 0
            for (i in subtitleTimeline.indices) {
                if (!subtitleTimeline[i]) continue
                subtitleActiveCount++
                val vadIdx = i + offsetSteps
                if (vadIdx in vadTimeline.indices && vadTimeline[vadIdx]) truePositive++
            }
            val precision = if (subtitleActiveCount > 0) truePositive.toDouble() / subtitleActiveCount else 0.0
            val recall = if (vadActiveCount > 0) truePositive.toDouble() / vadActiveCount else 0.0
            return if (precision + recall > 0.0) 2.0 * precision * recall / (precision + recall) else 0.0
        }

        val scores = mutableListOf<Pair<Int, Double>>()
        var offsetSteps = -maxOffsetSteps
        while (offsetSteps <= maxOffsetSteps) {
            scores.add(offsetSteps to scoreAt(offsetSteps))
            offsetSteps += searchStepInSteps
        }

        val (bestOffsetSteps, bestScore) = scores.maxByOrNull { it.second } ?: (0 to 0.0)
        val exclusionRadius = searchStepInSteps * 3
        val secondBestScore = scores
            .filter { (offset, _) -> abs(offset - bestOffsetSteps) > exclusionRadius }
            .maxOfOrNull { it.second } ?: 0.0

        val offsetMs = bestOffsetSteps * TIMELINE_STEP_MS
        return Triple(offsetMs, bestScore, secondBestScore.coerceAtLeast(0.0))
    }

    private fun singleWindowConfidence(w: WindowResult): Float {
        if (w.bestF1 <= 0.0) return 0f
        val sharpness = ((w.bestF1 - w.secondBestF1) / w.bestF1.coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
        val coverageFactor = (w.subtitleCoverage / 0.15f).coerceIn(0f, 1f)
        val combined = (w.bestF1.toFloat() * 0.5f) + (sharpness.toFloat() * 0.35f) + (coverageFactor * 0.15f)
        return combined.coerceIn(0f, 1f)
    }

    // Multiple independently-analyzed windows landing on the same offset
    // is much stronger evidence than one window's internal sharpness —
    // this rewards both a high average F1 AND a tight spread between
    // windows, with an extra bonus when all three windows (not just two)
    // agree.
    private fun multiWindowFlatConfidence(avgF1: Double, windowCount: Int, spreadMs: Long): Float {
        val f1Component = avgF1.toFloat().coerceIn(0f, 1f)
        val agreementComponent = (1f - (spreadMs.toFloat() / AGREEMENT_TOLERANCE_MS)).coerceIn(0f, 1f)
        val countBonus = if (windowCount >= 3) 0.15f else 0.05f
        return (f1Component * 0.55f + agreementComponent * 0.30f + countBonus).coerceIn(0f, 1f)
    }

    // Drift is a bigger claim than a flat offset — it's reshaping every
    // timestamp in the file, not just shifting them all by the same
    // amount — so this is deliberately harder to earn high confidence on
    // than the flat case above, even with a good fit.
    private fun driftConfidence(avgF1: Double, residualMs: Double): Float {
        val f1Component = avgF1.toFloat().coerceIn(0f, 1f)
        val fitComponent = (1f - (residualMs.toFloat() / MAX_DRIFT_RESIDUAL_MS)).coerceIn(0f, 1f)
        return (f1Component * 0.5f + fitComponent * 0.5f).coerceIn(0f, 1f)
    }
}
