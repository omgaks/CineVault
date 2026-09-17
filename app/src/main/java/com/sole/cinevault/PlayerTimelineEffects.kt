package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import com.sole.cinevault.library.savePlaybackPosition
import kotlinx.coroutines.delay

/**
 * 1B.2R — timeline/runtime housekeeping.
 *
 * The former dropped-frame listener called seekTo(currentPosition) whenever
 * a decoder reported a burst of dropped frames. On healthy local HEVC playback
 * that can itself flush/rebuffer the decoder, producing the exact
 * pause/buffer/resume loop and play/pause icon flicker seen on-device.
 *
 * Playback Resilience already owns decoder health/recovery. Timeline code must
 * observe playback, not mutate it in response to a performance statistic.
 */
@Composable
internal fun PlayerTimelineEffects(
    context: Context,
    player: ExoPlayer,
    videoPath: String,
    isStreamMedia: Boolean,
    isDraggingSeekbar: Boolean,
    isBuffering: Boolean,
    showSeekPreview: Boolean,
    previewPosition: Long,
    duration: Long,
    previewReloadKey: Int,
    droppedFrameNudgeCount: Int,
    lastNudgeAtMs: Long,
    onPositionChanged: (Long) -> Unit,
    onDurationChanged: (Long) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingSpinnerChanged: (Boolean) -> Unit,
    onStuckBufferingChanged: (Boolean) -> Unit,
    onDroppedFrameNudgeCountChanged: (Int) -> Unit,
    onLastNudgeAtMsChanged: (Long) -> Unit,
    onPreviewFramesChanged: (List<VideoThumbnailHelper.PreviewFrame>) -> Unit,
    onPreviewBitmapChanged: (Bitmap?) -> Unit,
    onSeekPreviewLargeChanged: (Boolean) -> Unit,
) {
    // droppedFrameNudgeCount / lastNudgeAtMs and their callbacks remain in
    // the signature for source compatibility with the existing host. They
    // are intentionally no longer used to seek/restart playback.

    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(400)
            if (isBuffering) onBufferingSpinnerChanged(true)
        } else {
            onBufferingSpinnerChanged(false)
            onStuckBufferingChanged(false)
        }
    }

    LaunchedEffect(isBuffering, videoPath) {
        if (isBuffering) {
            delay(15_000)
            if (isBuffering) onStuckBufferingChanged(true)
        }
    }

    LaunchedEffect(player, isDraggingSeekbar) {
        while (true) {
            if (!isDraggingSeekbar) {
                onPositionChanged(player.currentPosition.coerceAtLeast(0L))
            }
            onDurationChanged(player.duration.coerceAtLeast(1L))
            onPlayingChanged(player.isPlaying)
            delay(350)
        }
    }

    LaunchedEffect(videoPath, duration, previewReloadKey) {
        if (!isStreamMedia && duration > 1000L) {
            onPreviewFramesChanged(emptyList())

            val quick = VideoThumbnailHelper.generatePreviewCache(
                context,
                videoPath,
                duration,
                18,
            )
            if (quick.isNotEmpty()) {
                onPreviewFramesChanged(quick)
                quick.firstOrNull()?.bitmap?.let(onPreviewBitmapChanged)
            }

            val dense = VideoThumbnailHelper.generatePreviewCache(
                context,
                videoPath,
                duration,
                72,
            )
            if (dense.isNotEmpty()) {
                onPreviewFramesChanged(dense)
            }
        } else {
            onPreviewFramesChanged(emptyList())
            onPreviewBitmapChanged(null)
        }
    }

    LaunchedEffect(showSeekPreview, previewPosition) {
        if (showSeekPreview) {
            onSeekPreviewLargeChanged(false)
            delay(650)
            if (showSeekPreview) {
                onSeekPreviewLargeChanged(true)
            }
        } else {
            onSeekPreviewLargeChanged(false)
        }
    }

    LaunchedEffect(videoPath, isStreamMedia) {
        while (true) {
            delay(5000)
            val current = player.currentPosition.coerceAtLeast(0L)
            val total = player.duration.coerceAtLeast(1L)

            if (
                !isStreamMedia &&
                current > 5000L &&
                current < total - 5000L
            ) {
                savePlaybackPosition(context, videoPath, current)
                recordWatchHistory(
                    context,
                    videoPath,
                    cleanVideoTitle(videoPath)
                )
            }
        }
    }
}
