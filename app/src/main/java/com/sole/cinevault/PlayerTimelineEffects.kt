package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.sole.cinevault.library.VideoThumbnailHelper
import com.sole.cinevault.library.savePlaybackPosition
import kotlinx.coroutines.delay

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
    DisposableEffect(player, videoPath, droppedFrameNudgeCount, lastNudgeAtMs) {
        val analyticsListener = object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                if (droppedFrames < 8) return
                if (droppedFrameNudgeCount >= 3) return
                val now = System.currentTimeMillis()
                if (now - lastNudgeAtMs < 90_000L) return
                onLastNudgeAtMsChanged(now)
                onDroppedFrameNudgeCountChanged(droppedFrameNudgeCount + 1)
                player.seekTo(player.currentPosition)
            }
        }
        player.addAnalyticsListener(analyticsListener)
        onDispose { player.removeAnalyticsListener(analyticsListener) }
    }

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
            if (dense.isNotEmpty()) onPreviewFramesChanged(dense)
        } else {
            onPreviewFramesChanged(emptyList())
            onPreviewBitmapChanged(null)
        }
    }

    LaunchedEffect(showSeekPreview, previewPosition) {
        if (showSeekPreview) {
            onSeekPreviewLargeChanged(false)
            delay(650)
            if (showSeekPreview) onSeekPreviewLargeChanged(true)
        } else {
            onSeekPreviewLargeChanged(false)
        }
    }

    LaunchedEffect(videoPath, isStreamMedia) {
        while (true) {
            delay(5000)
            val current = player.currentPosition.coerceAtLeast(0L)
            val total = player.duration.coerceAtLeast(1L)
            if (!isStreamMedia && current > 5000L && current < total - 5000L) {
                savePlaybackPosition(context, videoPath, current)
                recordWatchHistory(context, videoPath, cleanVideoTitle(videoPath))
            }
        }
    }
}
