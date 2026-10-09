package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sole.cinevault.segments.DETECTED_SOURCE
import com.sole.cinevault.segments.FilmEnvelope
import com.sole.cinevault.segments.FilmLoudness
import com.sole.cinevault.segments.FilmLoudnessScanner
import com.sole.cinevault.segments.SceneDetectionRunner
import com.sole.cinevault.segments.SceneDetectionStatus
import com.sole.cinevault.segments.SceneDetector
import com.sole.cinevault.segments.SceneGuess
import com.sole.cinevault.segments.SegmentType
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentResult
import com.sole.cinevault.segments.isDetectedSegment
import kotlinx.coroutines.delay

private fun SmartSegment.isScene() =
    type == SegmentType.MID_CREDITS_SCENE || type == SegmentType.POST_CREDITS_SCENE

/**
 * Measures the loudness of a local film once (cached) so the seek bar can draw the real waves
 * and the scene detector has audio to work with. Starts a few seconds after playback begins so
 * it never competes with the film starting up.
 */
@Composable
internal fun PlayerFilmLoudnessEffect(
    scanner: FilmLoudnessScanner,
    filePath: String,
    duration: Long,
    seed: Int
) {
    LaunchedEffect(filePath, duration) {
        FilmLoudness.reset(seed)
        if (duration < 5 * 60_000L || !scanner.canScan(filePath)) return@LaunchedEffect
        delay(4_000)
        scanner.scan(
            key = "$filePath|$duration",
            path = filePath,
            durationMs = duration,
            onUpdate = { FilmLoudness.db = it },
            onFailure = { FilmLoudness.failure = it }
        )
    }
}

/**
 * For a film flagged (TMDB) as having a mid- and/or post-credit scene but with no exact time:
 * works out where each probably starts from the film's audio and subtitles, and adds them to the
 * segments with a confidence. Runs as soon as the end of the film has been measured; a remembered
 * result is applied at once. If a subtitle is loaded later it looks again.
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
    val hasProviderScene = result.segments.any { it.isScene() && !isDetectedSegment(it) }
    val creditsStart = result.segments.firstOrNull { it.type == SegmentType.CREDITS }?.startMs
        ?: SceneDetector.estimatedCreditsStartMs(duration)
    val eligible = !isTvEpisode && flagged && !hasProviderScene && duration >= 10 * 60_000L && creditsStart != null

    val db = FilmLoudness.db
    val failure = FilmLoudness.failure
    val tailReady = creditsStart != null && db != null &&
        FilmEnvelope.isMeasured(db, ((creditsStart - 60_000L).coerceAtLeast(0L) / 1000L).toInt(), FilmEnvelope.secondsFor(duration))
    val closeToCredits = creditsStart != null && position >= creditsStart - 60_000L
    val readyToLook = tailReady || (failure != null && position >= (creditsStart ?: 0L) - 300_000L) || closeToCredits
    val subtitleKey = subtitleUri?.toString()

    val key = "$filePath|$duration"
    // The subtitle the last look used; "-" = none. A different subtitle gets a fresh look.
    val lookedWith = remember(key) { arrayOfNulls<String>(1) }
    val foundWithSubtitles = remember(key) { BooleanArray(1) }
    val lastScenes = remember(key) { ArrayList<SceneGuess>() }
    val hasDetectedScene = result.segments.any { it.isScene() && isDetectedSegment(it) }

    // If the segments are reloaded from the data source they lose our guesses; put them back.
    LaunchedEffect(key, eligible, hasDetectedScene) {
        if (eligible && !hasDetectedScene && lastScenes.isNotEmpty()) {
            latestOnResult(withDetectedScenes(latestResult, lastScenes.toList(), duration))
        }
    }

    LaunchedEffect(key, eligible) { SceneDetectionStatus.note = null }

    LaunchedEffect(key, eligible, readyToLook, subtitleKey) {
        if (!eligible || creditsStart == null || !readyToLook) return@LaunchedEffect
        val subtitleTag = subtitleKey ?: "-"
        val first = lookedWith[0] == null
        if (!first && (lookedWith[0] == subtitleTag || foundWithSubtitles[0])) return@LaunchedEffect

        fun inject(guesses: List<SceneGuess>) {
            lastScenes.clear(); lastScenes.addAll(guesses)
            latestOnResult(withDetectedScenes(latestResult, guesses, duration))
        }

        if (first) {
            val remembered = runner.cached(key)
            if (remembered.isNotEmpty()) {
                lookedWith[0] = subtitleTag
                foundWithSubtitles[0] = remembered.any { it.usedSubtitles }
                inject(remembered)
                return@LaunchedEffect
            }
        }

        lookedWith[0] = subtitleTag
        val outcome = runner.detect(
            key = key,
            filePath = filePath,
            durationMs = duration,
            creditsStartMs = creditsStart,
            subtitleUri = latestSubtitle,
            hasMid = latestResult.hasMidCreditsScene,
            hasPost = latestResult.hasPostCreditsScene,
            filmDb = FilmLoudness.db,
            audioFailure = FilmLoudness.failure
        )
        SceneDetectionStatus.note = outcome.note
        foundWithSubtitles[0] = outcome.scenes.any { it.usedSubtitles }
        if (outcome.scenes.isNotEmpty()) inject(outcome.scenes)
    }
}

/** [current] with its guessed scenes replaced by [guesses] (mid/post decided by TMDB flags). */
private fun withDetectedScenes(current: SmartSegmentResult, guesses: List<SceneGuess>, duration: Long): SmartSegmentResult {
    val kept = current.segments.filterNot { it.isScene() && isDetectedSegment(it) }
    val both = current.hasMidCreditsScene && current.hasPostCreditsScene
    val added = guesses.sortedBy { it.startMs }.mapIndexed { index, guess ->
        val type = when {
            both && guesses.size >= 2 -> if (index == 0) SegmentType.MID_CREDITS_SCENE else SegmentType.POST_CREDITS_SCENE
            both -> if (duration - guess.startMs < 4 * 60_000L) SegmentType.POST_CREDITS_SCENE else SegmentType.MID_CREDITS_SCENE
            current.hasPostCreditsScene -> SegmentType.POST_CREDITS_SCENE
            else -> SegmentType.MID_CREDITS_SCENE
        }
        val evidence = listOfNotNull(
            if (guess.usedAudio) "audio" else null,
            if (guess.usedSubtitles) "subtitles" else null
        ).joinToString("+")
        SmartSegment(
            type = type,
            startMs = guess.startMs,
            endMs = duration,
            source = "$DETECTED_SOURCE:$evidence",
            confidence = guess.confidence / 100f
        )
    }
    return current.copy(segments = kept + added)
}
