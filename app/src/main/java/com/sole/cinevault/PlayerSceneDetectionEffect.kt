package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sole.cinevault.segments.DETECTED_SOURCE
import com.sole.cinevault.segments.SceneDetectionRunner
import com.sole.cinevault.segments.SceneDetector
import com.sole.cinevault.segments.SceneGuess
import com.sole.cinevault.segments.SegmentType
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentResult

private fun SmartSegment.isScene() =
    type == SegmentType.MID_CREDITS_SCENE || type == SegmentType.POST_CREDITS_SCENE

/**
 * For a film flagged as having a scene after the credits but with no exact time, works out a
 * likely start from audio and subtitles and adds it to the segments, with its confidence.
 * A remembered guess is applied straight away; fresh analysis starts two minutes before the
 * credits so it never competes with the film's own decoding earlier on.
 */
@Composable
internal fun PlayerSceneDetectionEffect(
    runner: SceneDetectionRunner,
    filePath: String,
    isTvEpisode: Boolean,
    result: SmartSegmentResult,
    position: Long,
    duration: Long,
    subtitleUri: Uri?,
    onResult: (SmartSegmentResult) -> Unit
) {
    val latestResult by rememberUpdatedState(result)
    val latestSubtitle by rememberUpdatedState(subtitleUri)
    val latestOnResult by rememberUpdatedState(onResult)

    val flagged = result.hasMidCreditsScene || result.hasPostCreditsScene
    val hasAnyScene = result.segments.any { it.isScene() }
    val creditsStart = result.segments.firstOrNull { it.type == SegmentType.CREDITS }?.startMs
        ?: SceneDetector.estimatedCreditsStartMs(duration)
    val eligible = !isTvEpisode && flagged && !hasAnyScene && duration >= 10 * 60_000L && creditsStart != null
    val near = creditsStart != null && position >= creditsStart - 120_000L

    val key = "$filePath|$duration"
    val attempted = remember(key) { BooleanArray(1) }

    LaunchedEffect(key, eligible, near) {
        if (!eligible || attempted[0] || creditsStart == null) return@LaunchedEffect

        fun inject(guess: SceneGuess) {
            val current = latestResult
            val type = if (current.hasPostCreditsScene) SegmentType.POST_CREDITS_SCENE else SegmentType.MID_CREDITS_SCENE
            val evidence = listOfNotNull(
                if (guess.usedAudio) "audio" else null,
                if (guess.usedSubtitles) "subtitles" else null
            ).joinToString("+")
            latestOnResult(
                current.copy(
                    segments = current.segments + SmartSegment(
                        type = type,
                        startMs = guess.startMs,
                        endMs = duration,
                        source = "$DETECTED_SOURCE:$evidence",
                        confidence = guess.confidence / 100f
                    )
                )
            )
        }

        runner.cached(key)?.let { cached ->
            attempted[0] = true
            if (cached.confidence >= SceneDetector.MIN_SHOWN_CONFIDENCE) inject(cached)
            return@LaunchedEffect
        }
        if (!near) return@LaunchedEffect

        attempted[0] = true
        val guess = runner.detect(
            key = key,
            filePath = filePath,
            durationMs = duration,
            creditsStartMs = creditsStart,
            subtitleUri = latestSubtitle,
            hasPost = latestResult.hasPostCreditsScene
        )
        if (guess != null && guess.confidence >= SceneDetector.MIN_SHOWN_CONFIDENCE) inject(guess)
    }
}
