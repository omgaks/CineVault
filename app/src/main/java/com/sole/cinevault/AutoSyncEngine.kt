package com.sole.cinevault

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

// ── Auto-Sync engine — Phase 1: Speech-Timing Alignment ─────────────────
// Compares WHEN speech happens in the audio against WHEN subtitle cues are
// active, and finds the flat time-shift that best lines them up. This is
// timing-only — it does NOT know what anyone is saying, so it works across
// language mismatches, but it can be fooled by music, sound effects, or
// dense SDH cues. That's exactly why confidence is computed from real
// signal quality (peak sharpness + dialogue coverage) rather than assumed.
//
// Phase 2 (multi-anchor drift) and Phase 3 (Whisper-style text matching)
// are NOT implemented here — this only ever returns a single flat offset,
// reusing the exact -10s..+10s range the manual Sync slider already uses,
// so nothing Auto-Sync produces is a result the person couldn't have
// reached by hand.
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

    // Single window near the start for Phase 1 (no multi-anchor drift
    // yet) — skips the first minute to dodge studio-logo silence/opening
    // music, which is more likely to fool VAD than real dialogue.
    private const val WINDOW_START_MS = 60_000L
    private const val WINDOW_DURATION_MS = 6 * 60_000L

    // Matches the manual Sync slider's own -10s..+10s range elsewhere in
    // the app — Auto-Sync can't produce a result the manual controls
    // couldn't also represent.
    private const val MAX_OFFSET_MS = 10_000L
    // FIX: this used to be 50L, but SEARCH_STEP_MS / TIMELINE_STEP_MS (20L)
    // is integer division — 50/20 truncates to 2, making the ACTUAL search
    // step 40ms regardless of what this constant said. Set to the true
    // value rather than changing search granularity as a side effect of
    // fixing the discrepancy.
    private const val SEARCH_STEP_MS = 40L
    private const val TIMELINE_STEP_MS = 20L

    private val TIMING_REGEX = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")

    // FIX: now suspend — required since extractWindow() (called below) is
    // now itself suspend/cancellation-aware, and this whole operation can
    // run for real seconds of CPU work that should actually stop if the
    // surrounding coroutine is cancelled (video changed, screen closed).
    suspend fun run(
        context: Context,
        videoPath: String,
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

        val extracted = AutoSyncAudioExtractor.extractWindow(
            context = context,
            filePath = videoPath,
            trackLanguage = audioTrackLanguage,
            startMs = WINDOW_START_MS,
            durationMs = WINDOW_DURATION_MS
        ) ?: return AutoSyncStatus.Failed("Couldn't decode audio from this file for analysis.")

        if (extracted.samples.isEmpty()) {
            return AutoSyncStatus.Failed("No audio decoded in the sampled window.")
        }

        val vadTimeline = try {
            buildVadTimeline(context, extracted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AutoSyncStatus.Failed("Speech detector failed to load: ${e.message ?: e.javaClass.simpleName}")
        }

        if (vadTimeline.none { it }) {
            return AutoSyncStatus.Failed("No speech detected in the sampled portion of this video — the audio track may be music/commentary only, or try a different section.")
        }

        val subtitleTimeline = buildSubtitleTimeline(subtitleSrtText, WINDOW_START_MS, WINDOW_DURATION_MS)

        if (subtitleTimeline.none { it }) {
            return AutoSyncStatus.Failed("No subtitle cues found in the sampled time range.")
        }

        val (bestOffsetMs, score, secondBestScore) = searchBestOffset(subtitleTimeline, vadTimeline)
        val confidence = computeConfidence(score, secondBestScore, subtitleTimeline)

        val result = SubtitleSyncResult(
            initialOffsetMs = bestOffsetMs,
            timeScale = 1.0,
            anchors = listOf(SyncAnchor(WINDOW_START_MS, WINDOW_START_MS + bestOffsetMs)),
            confidence = confidence,
            method = SyncMethod.SPEECH_TIMING,
            averageErrorMs = 0L
        )

        return if (confidence >= 0.6f) AutoSyncStatus.Success(result) else AutoSyncStatus.LowConfidence(result)
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

            // Uses compute() directly per VAD-native chunk (continuous
            // probability per window) rather than the segment queue
            // (empty()/pop()/front()), since cross-correlation needs a
            // continuous active/inactive timeline, not discrete segments.
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

    // Returns (bestOffsetMs, bestScore, secondBestScore) — the second-best
    // score (from a DIFFERENT, non-adjacent offset) is what confidence is
    // actually computed from: a sharp, isolated peak means the alignment
    // is unambiguous; a flat/noisy score curve with many similar peaks
    // means the result is a coin flip dressed up as a number.
    private fun searchBestOffset(subtitleTimeline: BooleanArray, vadTimeline: BooleanArray): Triple<Long, Double, Double> {
        val maxOffsetSteps = (MAX_OFFSET_MS / TIMELINE_STEP_MS).toInt()
        val searchStepInSteps = (SEARCH_STEP_MS / TIMELINE_STEP_MS).toInt().coerceAtLeast(1)

        fun scoreAt(offsetSteps: Int): Double {
            var overlap = 0
            var subtitleActiveCount = 0
            for (i in subtitleTimeline.indices) {
                if (!subtitleTimeline[i]) continue
                subtitleActiveCount++
                val vadIdx = i + offsetSteps
                if (vadIdx in vadTimeline.indices && vadTimeline[vadIdx]) overlap++
            }
            return if (subtitleActiveCount > 0) overlap.toDouble() / subtitleActiveCount else 0.0
        }

        // FIX: previously a single pass that set secondBestScore to
        // whatever the PREVIOUS best was every time a new best was found —
        // on a smooth score curve (the normal shape near a true peak),
        // that previous best is almost always immediately ADJACENT to the
        // new one, so "second best" ended up being a near-duplicate of the
        // peak itself rather than a genuinely different candidate offset.
        // That silently inflated the sharpness score computeConfidence()
        // relies on. Two passes now: find the real best first, then only
        // consider offsets far enough away (>3 search steps) as candidates
        // for second-best, exactly matching what the confidence math
        // documents it's doing.
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

    private fun computeConfidence(bestScore: Double, secondBestScore: Double, subtitleTimeline: BooleanArray): Float {
        if (bestScore <= 0.0) return 0f
        // Peak sharpness — how much better the best offset is than the
        // next-best DIFFERENT offset. A flat curve (best ≈ second-best)
        // means many offsets look equally plausible, i.e. low confidence.
        val sharpness = ((bestScore - secondBestScore) / bestScore.coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
        // Coverage — how much of the sampled window actually had dialogue
        // to work with in the first place. A window that's mostly silence
        // gives few real anchors even if the overlap score looks fine.
        val subtitleCoverage = subtitleTimeline.count { it }.toFloat() / subtitleTimeline.size.coerceAtLeast(1)
        val coverageFactor = (subtitleCoverage / 0.15f).coerceIn(0f, 1f)

        val combined = (bestScore.toFloat() * 0.5f) + (sharpness.toFloat() * 0.35f) + (coverageFactor * 0.15f)
        return combined.coerceIn(0f, 1f)
    }
}
