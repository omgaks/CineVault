package com.sole.cinevault.segments

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds where a mid- or post-credit scene probably starts, when no exact time is known.
 *
 * Three independent signals are combined, each with its own confidence:
 *  - the TMDB flag ("this film has a scene after the credits") decides whether to look at all;
 *  - the SUBTITLES: credits carry no dialogue, so the first line after a long silence in the
 *    last minutes is very likely the scene;
 *  - the AUDIO: credits music usually stops for a moment before the scene, then sound returns.
 *
 * Every number here is a heuristic, not a measurement. The confidence is how much the signals
 * agree, and it is shown to the user as exactly that. Pure Kotlin so it runs under plain JVM tests.
 */
data class SubtitleLine(val startMs: Long, val endMs: Long, val text: String)

/** RMS level of the audio, one value per [hopMs], the first value starting at [startMs]. */
class AudioEnvelope(val startMs: Long, val hopMs: Int, val rms: FloatArray)

data class SceneCandidate(val startMs: Long, val confidence: Int)

data class SceneGuess(
    val startMs: Long,
    val confidence: Int,
    val usedAudio: Boolean,
    val usedSubtitles: Boolean
)

/** Remembers the guess per film so the audio is only decoded once. */
internal object SceneGuessCodec {
    fun encode(g: SceneGuess) = "${g.startMs},${g.confidence},${if (g.usedAudio) 1 else 0},${if (g.usedSubtitles) 1 else 0}"
    fun decode(raw: String?): SceneGuess? {
        val p = raw?.split(',') ?: return null
        if (p.size != 4) return null
        val start = p[0].toLongOrNull() ?: return null
        val conf = p[1].toIntOrNull() ?: return null
        if (start < 0 || conf !in 0..100) return null
        return SceneGuess(start, conf, p[2] == "1", p[3] == "1")
    }
}

object SceneDetector {

    /** Below this the guess is not offered as a skip button. */
    const val MIN_SHOWN_CONFIDENCE = 45
    const val ENVELOPE_HOP_MS = 500

    private const val AGREE_WITHIN_MS = 15_000L
    private const val MIN_SUBTITLE_GAP_MS = 75_000L
    private const val MIN_SILENCE_MS = 2_500L
    private const val MIN_SCENE_REMAINING_MS = 10_000L

    /** Where the credits probably begin when nothing says so: the last 7%, clamped to 4-10 minutes. */
    fun estimatedCreditsStartMs(durationMs: Long): Long? {
        if (durationMs <= 0L) return null
        val tail = (durationMs * 7 / 100).coerceIn(4 * 60_000L, 10 * 60_000L)
        return (durationMs - tail).coerceAtLeast(0L)
    }

    // --- Audio ----------------------------------------------------------------------------

    /** RMS per hop from mono samples. */
    fun envelopeOf(samples: FloatArray, sampleRate: Int, startMs: Long, hopMs: Int = ENVELOPE_HOP_MS): AudioEnvelope {
        val perHop = max(1, sampleRate * hopMs / 1000)
        val count = samples.size / perHop
        val out = FloatArray(count)
        for (h in 0 until count) {
            var sum = 0.0
            val from = h * perHop
            for (i in from until from + perHop) sum += samples[i].toDouble() * samples[i]
            out[h] = sqrt(sum / perHop).toFloat()
        }
        return AudioEnvelope(startMs, hopMs, out)
    }

    private fun db(v: Float): Float = (20.0 * log10(v.toDouble() + 1e-6)).toFloat()

    /**
     * Every "quiet for a few seconds, then sound that carries on" moment inside the credits,
     * each with a score 0..1. [searchFromMs] is where looking starts.
     */
    private fun audioCandidates(
        env: AudioEnvelope,
        creditsStartMs: Long,
        durationMs: Long,
        searchFromMs: Long
    ): List<Pair<Long, Double>> {
        val n = env.rms.size
        if (n < 40 || durationMs <= 0L) return emptyList()
        val hop = env.hopMs
        val levels = FloatArray(n) { db(env.rms[it]) }

        // Reference loudness: the credits' own music near their start.
        val refFrom = indexAt(env, creditsStartMs).coerceIn(0, n - 1)
        val refTo = min(n, refFrom + 120_000 / hop)
        if (refTo - refFrom < 10) return emptyList()
        val reference = median(levels.copyOfRange(refFrom, refTo))
        if (reference < -60f) return emptyList() // the credits are effectively silent
        val quietBelow = reference - 18f

        val minSilenceHops = max(1, ceil(MIN_SILENCE_MS.toDouble() / hop).toInt())
        val candidates = ArrayList<Pair<Long, Double>>() // time, score 0..1
        var i = indexAt(env, searchFromMs).coerceIn(0, n)
        while (i < n) {
            if (levels[i] >= quietBelow) { i++; continue }
            var j = i
            while (j < n && levels[j] < quietBelow) j++
            val silentHops = j - i
            if (silentHops >= minSilenceHops && j < n) {
                val onsetMs = env.startMs + j.toLong() * hop
                val remaining = durationMs - onsetMs
                if (remaining >= MIN_SCENE_REMAINING_MS) {
                    val after = levels.copyOfRange(j, min(n, j + 20_000 / hop))
                    if (after.size >= 12 * 500 / hop) {
                        val loudShare = after.count { it >= quietBelow + 6f }.toDouble() / after.size
                        val contrast = (after.average() - levels.copyOfRange(i, j).average())
                        val gapScore = min(1.0, (silentHops * hop / 1000.0 - 2.5) / 8.0)
                        val contrastScore = min(1.0, max(0.0, (contrast - 12.0) / 20.0))
                        val remainingScore = when {
                            remaining <= 5 * 60_000L -> 1.0
                            remaining <= 10 * 60_000L -> 0.6
                            else -> 0.25
                        }
                        val score = (0.35 * gapScore + 0.30 * contrastScore + 0.35 * remainingScore) * loudShare
                        if (score >= 0.30) candidates.add((onsetMs - 300L).coerceAtLeast(0L) to score)
                    }
                }
            }
            i = max(j, i + 1)
        }
        return candidates
    }

    // Audio alone cannot be certain: it caps at 70%.
    private fun audioConfidence(score: Double) = (score * 100.0 * 0.95).toInt().coerceIn(20, 70)

    /** The single best audio moment (latest of the near-best for a post-credit scene, else earliest). */
    fun fromAudio(
        env: AudioEnvelope,
        creditsStartMs: Long,
        durationMs: Long,
        preferLatest: Boolean
    ): SceneCandidate? {
        val candidates = audioCandidates(env, creditsStartMs, durationMs, creditsStartMs + 40_000L)
        if (candidates.isEmpty()) return null
        val best = candidates.maxOf { it.second }
        val near = candidates.filter { it.second >= best - 0.10 }
        val pick = if (preferLatest) near.maxBy { it.first } else near.minBy { it.first }
        return SceneCandidate(pick.first, audioConfidence(pick.second))
    }

    /**
     * Up to [max] separate audio moments, for films with both a mid- and a post-credit scene.
     * Needs a loudness record that covers the credits; [searchFromMs] starts a little before them
     * because a mid-credit scene can begin almost as the credits do.
     */
    fun fromAudioAll(
        env: AudioEnvelope,
        creditsStartMs: Long,
        durationMs: Long,
        max: Int = 2,
        searchFromMs: Long = creditsStartMs - 30_000L
    ): List<SceneCandidate> {
        val strongest = audioCandidates(env, creditsStartMs, durationMs, searchFromMs)
            .sortedByDescending { it.second }
        val kept = ArrayList<Pair<Long, Double>>()
        for (c in strongest) {
            if (kept.size >= max) break
            if (kept.none { abs(it.first - c.first) < 60_000L }) kept.add(c)
        }
        return kept.sortedBy { it.first }.map { SceneCandidate(it.first, audioConfidence(it.second)) }
    }

    // --- Subtitles ------------------------------------------------------------------------

    private val adLine = Regex(
        "opensubtitles|subscene|yify|yts\\b|www\\.|\\.com|\\.org|\\.net|downloaded|subtitles? by|" +
            "synced|translated by|encoded by|advertis|support us|rate this",
        RegexOption.IGNORE_CASE
    )

    private data class SubtitleGap(val startMs: Long, val gapMs: Long)

    private fun subtitleGaps(lines: List<SubtitleLine>, durationMs: Long, maxRemainingMs: Long): List<SubtitleGap> {
        if (durationMs <= 0L) return emptyList()
        val windowStart = durationMs - 16 * 60_000L
        val speech = lines
            .filter { !adLine.containsMatchIn(it.text) && it.startMs in 0 until durationMs }
            .sortedBy { it.startMs }
        if (speech.size < 20) return emptyList() // too few lines to trust "silence" - e.g. forced subs only
        val out = ArrayList<SubtitleGap>()
        for (k in 1 until speech.size) {
            val start = speech[k].startMs
            val remaining = durationMs - start
            if (start < windowStart) continue
            if (remaining < MIN_SCENE_REMAINING_MS || remaining > maxRemainingMs) continue
            val gap = start - speech[k - 1].endMs
            if (gap >= MIN_SUBTITLE_GAP_MS) out.add(SubtitleGap(start, gap))
        }
        return out
    }

    private fun subtitleCandidate(g: SubtitleGap, durationMs: Long): SceneCandidate {
        val gapSec = g.gapMs / 1000.0
        var conf = 45 + min(30.0, (gapSec - 75.0) / 5.0)
        if (durationMs - g.startMs > 6 * 60_000L) conf -= 10 // mid-credit territory: less certain
        // Scenes begin a few seconds before the first spoken line.
        val start = max(g.startMs - 2_500L, g.startMs - g.gapMs)
        return SceneCandidate(start, conf.toInt().coerceIn(20, 80))
    }

    /**
     * The first dialogue after the longest silent stretch near the end. Credits have no
     * dialogue, so a line that appears after minutes of nothing is almost always the scene.
     */
    fun fromSubtitles(
        lines: List<SubtitleLine>,
        durationMs: Long,
        hasPost: Boolean
    ): SceneCandidate? {
        val maxRemaining = if (hasPost) 6 * 60_000L else 14 * 60_000L
        val best = subtitleGaps(lines, durationMs, maxRemaining).maxByOrNull { it.gapMs } ?: return null
        return subtitleCandidate(best, durationMs)
    }

    /** Every long silent stretch near the end, for films that may have two scenes. */
    fun fromSubtitlesAll(
        lines: List<SubtitleLine>,
        durationMs: Long,
        hasMid: Boolean,
        hasPost: Boolean
    ): List<SceneCandidate> {
        val maxRemaining = if (hasPost && !hasMid) 6 * 60_000L else 14 * 60_000L
        return subtitleGaps(lines, durationMs, maxRemaining).map { subtitleCandidate(it, durationMs) }
    }

    // --- Combining -------------------------------------------------------------------------

    /**
     * Fuses the signals into one guess. Agreement raises the confidence; disagreement keeps
     * the stronger signal but lowers it. Returns null when no signal found anything.
     */
    fun combine(audio: SceneCandidate?, subtitles: SceneCandidate?): SceneGuess? {
        if (audio == null && subtitles == null) return null
        if (audio != null && subtitles != null) {
            return if (abs(audio.startMs - subtitles.startMs) <= AGREE_WITHIN_MS) {
                val missA = 100 - audio.confidence
                val missS = 100 - subtitles.confidence
                val conf = (100 - missA * missS / 100).coerceIn(0, 95)
                // The audio onset is the more precise moment; the subtitle only confirms it.
                SceneGuess(min(audio.startMs, subtitles.startMs), conf, usedAudio = true, usedSubtitles = true)
            } else {
                val stronger = if (audio.confidence >= subtitles.confidence) audio else subtitles
                SceneGuess(
                    stronger.startMs,
                    (stronger.confidence - 15).coerceAtLeast(10),
                    usedAudio = stronger === audio,
                    usedSubtitles = stronger === subtitles
                )
            }
        }
        val only = audio ?: subtitles!!
        return SceneGuess(only.startMs, only.confidence, usedAudio = audio != null, usedSubtitles = subtitles != null)
    }

    /**
     * Fuses the lists from both signals into at most as many scenes as TMDB says the film has
     * (one when it flags one kind, two when it flags both). A subtitle gap and an audio moment
     * within 15 s of each other confirm one another. A lone audio moment is trusted less when
     * the subtitles had something to say elsewhere. Result is in film order.
     */
    fun combineAll(
        audio: List<SceneCandidate>,
        subtitles: List<SceneCandidate>,
        hasMid: Boolean,
        hasPost: Boolean
    ): List<SceneGuess> {
        val limit = max(1, (if (hasMid) 1 else 0) + (if (hasPost) 1 else 0))
        val audioUsed = BooleanArray(audio.size)
        val all = ArrayList<SceneGuess>()
        for (s in subtitles) {
            val match = audio.indices
                .filter { !audioUsed[it] && abs(audio[it].startMs - s.startMs) <= AGREE_WITHIN_MS }
                .minByOrNull { abs(audio[it].startMs - s.startMs) }
            if (match != null) {
                audioUsed[match] = true
                combine(audio[match], s)?.let { all.add(it) }
            } else {
                all.add(SceneGuess(s.startMs, s.confidence, usedAudio = false, usedSubtitles = true))
            }
        }
        val penalty = if (subtitles.isNotEmpty()) 15 else 0
        audio.forEachIndexed { i, a ->
            if (!audioUsed[i]) all.add(SceneGuess(a.startMs, (a.confidence - penalty).coerceAtLeast(10), usedAudio = true, usedSubtitles = false))
        }
        if (all.isEmpty()) return emptyList()

        if (limit == 1) {
            val best = all.maxOf { it.confidence }
            val near = all.filter { it.confidence >= best - 10 }
            val pick = if (hasPost && !hasMid) near.maxBy { it.startMs } else near.minBy { it.startMs }
            return listOf(pick)
        }
        val kept = ArrayList<SceneGuess>()
        for (g in all.sortedByDescending { it.confidence }) {
            if (kept.size >= limit) break
            if (kept.none { abs(it.startMs - g.startMs) < 60_000L }) kept.add(g)
        }
        return kept.sortedBy { it.startMs }
    }

    // --- helpers ---------------------------------------------------------------------------

    private fun indexAt(env: AudioEnvelope, timeMs: Long): Int =
        ((timeMs - env.startMs) / env.hopMs).toInt()

    private fun median(values: FloatArray): Float {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }
}
