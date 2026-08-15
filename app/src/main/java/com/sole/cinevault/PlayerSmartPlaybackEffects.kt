package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.segments.SegmentType
import com.sole.cinevault.segments.SmartSegmentRepository
import com.sole.cinevault.segments.SmartSegmentResult
import kotlinx.coroutines.delay

/**
 * Owns the smart-playback effects that sit between detected segments and
 * episode navigation. State remains in VideoPlayerScreen; this function only
 * performs the same effect orchestration through callbacks.
 */
@Composable
internal fun PlayerSmartPlaybackEffects(
    currentVideo: VideoFile,
    currentMeta: VideoWithMetadata?,
    duration: Long,
    position: Long,
    isCurrentTvShow: Boolean,
    episodeList: List<VideoWithMetadata>,
    smartSegmentRepository: SmartSegmentRepository,
    smartSegmentResult: SmartSegmentResult,
    showNextEpisodeOverlay: Boolean,
    pendingNextEpisode: VideoWithMetadata?,
    nextEpisodeDismissed: Boolean,
    isPlaying: Boolean,
    isVideoEnded: Boolean,
    onSmartSegmentResultChanged: (SmartSegmentResult) -> Unit,
    onQueueNextEpisode: (VideoWithMetadata) -> Unit,
    onClearNextEpisode: () -> Unit,
    onCountdownChanged: (Int) -> Unit,
    onCountdownFinished: (VideoWithMetadata) -> Unit,
) {
    LaunchedEffect(
        currentMeta?.video?.path,
        duration > 60_000L,
    ) {
        val meta = currentMeta
            ?: return@LaunchedEffect

        if (
            duration <= 60_000L ||
            meta.type == "secret"
        ) {
            return@LaunchedEffect
        }

        onSmartSegmentResultChanged(
            smartSegmentRepository.load(
                meta,
                duration,
            ),
        )
    }

    val creditsStartMs =
        smartSegmentResult.segments
            .firstOrNull {
                it.type == SegmentType.CREDITS
            }
            ?.startMs

    LaunchedEffect(
        currentVideo.path,
        position,
        creditsStartMs,
        showNextEpisodeOverlay,
    ) {
        if (
            !isCurrentTvShow ||
            showNextEpisodeOverlay ||
            nextEpisodeDismissed ||
            creditsStartMs == null ||
            position < creditsStartMs
        ) {
            return@LaunchedEffect
        }

        val index =
            episodeList.indexOfFirst {
                it.video.path == currentVideo.path
            }

        val next =
            episodeList.getOrNull(index + 1)
                ?: return@LaunchedEffect

        onQueueNextEpisode(next)
    }

    LaunchedEffect(
        position,
        creditsStartMs,
    ) {
        if (
            showNextEpisodeOverlay &&
            creditsStartMs != null &&
            position < creditsStartMs
        ) {
            onClearNextEpisode()
        }
    }

    LaunchedEffect(
        showNextEpisodeOverlay,
        pendingNextEpisode,
    ) {
        if (!showNextEpisodeOverlay) {
            return@LaunchedEffect
        }

        val queuedEpisode =
            pendingNextEpisode
                ?: return@LaunchedEffect

        var count = 15

        while (count > 0) {
            onCountdownChanged(count)
            delay(1_000)

            if (
                !showNextEpisodeOverlay ||
                pendingNextEpisode == null
            ) {
                return@LaunchedEffect
            }

            if (isPlaying || isVideoEnded) {
                count--
            }
        }

        onCountdownFinished(queuedEpisode)
    }
}
