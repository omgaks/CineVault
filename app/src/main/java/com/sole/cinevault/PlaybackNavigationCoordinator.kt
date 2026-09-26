package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackNavigationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val trackUi: SubtitleTrackSelectionState,
    private val coreUi: SubtitleCoreUiState,
    private val getEpisodeList: () -> List<VideoWithMetadata>,
    private val getCurrentVideo: () -> VideoFile,
    private val getIsStreamMedia: () -> Boolean,
    private val getPlaybackSpeed: () -> Float,
    private val setCurrentVideo: (VideoFile) -> Unit,
    private val setCurrentMediaType: (String) -> Unit,
    private val setEdgeSwipeHint: (String) -> Unit,
    private val setPlayerErrorMessage: (String?) -> Unit,
    private val setIsVideoEnded: (Boolean) -> Unit,
    private val onPlayNext: (VideoWithMetadata) -> Unit
) {
    fun playPrevious() {
        val episodeList = getEpisodeList()
        val idx = episodeList.indexOfFirst { it.video.path == getCurrentVideo().path }
        val prev = episodeList.getOrNull(idx - 1)
        if (prev != null) {
            setCurrentMediaType(prev.type); setCurrentVideo(prev.video); onPlayNext(prev); setEdgeSwipeHint("◀ Previous")
        } else setEdgeSwipeHint("No previous video")
        scope.launch { delay(1200); setEdgeSwipeHint("") }
    }

    fun playNext() {
        val episodeList = getEpisodeList()
        val idx = episodeList.indexOfFirst { it.video.path == getCurrentVideo().path }
        val next = episodeList.getOrNull(idx + 1)
        if (next != null) {
            setCurrentMediaType(next.type); setCurrentVideo(next.video); onPlayNext(next); setEdgeSwipeHint("Next ▶")
        } else setEdgeSwipeHint("No next video")
        scope.launch { delay(1200); setEdgeSwipeHint("") }
    }

    fun playCurrentVideoWithSubtitle(
        subtitleUri: Uri? = null,
        resumePosition: Long = 0L,
        isOriginalSubtitle: Boolean = true
    ) {
        val currentVideo = getCurrentVideo()
        val isSmbMedia = currentVideo.path.startsWith("smb://", ignoreCase = true)
        val isContentUriMedia = currentVideo.path.startsWith("content://", ignoreCase = true)
        if (!getIsStreamMedia() && !isSmbMedia && !isContentUriMedia &&
            !java.io.File(currentVideo.path).exists()) {
            setPlayerErrorMessage("File not found. It may have been moved, renamed, or the drive it's on was disconnected.")
            return
        }

        try {
            setPlayerErrorMessage(null)
            if (subtitleUri != null && isOriginalSubtitle) {
                trackUi.originalUri = subtitleUri
                trackUi.appliedOffsetMs = 0L
                coreUi.syncOffset = 0f
            }

            val mediaItem = buildPlaybackMediaItem(currentVideo.path, subtitleUri)
            val resumeAt = resumePosition.coerceAtLeast(0L)

            if (exoPlayer.mediaItemCount > 0) {
                // Subtitle/style re-application must not deliberately rebuild
                // the whole player session. Replacing the current MediaItem
                // lets Media3 retain the PlayerView/video surface while it
                // updates the item's subtitle configuration.
                val index = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
                exoPlayer.replaceMediaItem(index, mediaItem)
                exoPlayer.seekTo(index, resumeAt)
            } else {
                // True initial playback still requires preparation.
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.seekTo(resumeAt)
            }

            exoPlayer.playWhenReady = true
            exoPlayer.play()
            exoPlayer.playbackParameters = PlaybackParameters(getPlaybackSpeed())
            setIsVideoEnded(false)
        } catch (e: Exception) {
            setPlayerErrorMessage("Couldn't start playback: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
