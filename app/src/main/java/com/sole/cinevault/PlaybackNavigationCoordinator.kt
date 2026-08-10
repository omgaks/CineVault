package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.subtitles.detectSubtitleFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// FIX: fifth slice of extracting VideoPlayerScreen()'s behavior out of
// its own body (see AutoSyncCoordinator.kt, SubtitleSyncToolsCoordinator.kt,
// SubtitleDeletionCoordinator.kt, and PlaybackErrorFormatting.kt for the
// previous four slices and the full reasoning). This is the most
// heavily-depended-upon function extracted so far — playCurrentVideoWithSubtitle
// alone has 13 call sites elsewhere in VideoPlayerScreen.kt — but every
// one of them keeps working completely unchanged, same as every previous
// slice, since VideoPlayerScreen() still exposes a local wrapper function
// with the exact original name and signature.
//
// trackUi/coreUi are passed directly as stable class references (same
// reasoning as the second slice) since they're genuine PlayerUiState.kt
// instances, not raw composable vars. Only currentVideo/currentMediaType/
// edgeSwipeHint/playerErrorMessage/isVideoEnded need getter/setter
// lambdas — those really are plain composable-local `var`s with no
// stable object behind them.
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
        } else {
            setEdgeSwipeHint("No previous video")
        }
        scope.launch { delay(1200); setEdgeSwipeHint("") }
    }

    fun playNext() {
        val episodeList = getEpisodeList()
        val idx = episodeList.indexOfFirst { it.video.path == getCurrentVideo().path }
        val next = episodeList.getOrNull(idx + 1)
        if (next != null) {
            setCurrentMediaType(next.type); setCurrentVideo(next.video); onPlayNext(next); setEdgeSwipeHint("Next ▶")
        } else {
            setEdgeSwipeHint("No next video")
        }
        scope.launch { delay(1200); setEdgeSwipeHint("") }
    }

    fun playCurrentVideoWithSubtitle(subtitleUri: Uri? = null, resumePosition: Long = 0L, isOriginalSubtitle: Boolean = true) {
        val currentVideo = getCurrentVideo()
        val isSmbMedia = currentVideo.path.startsWith("smb://", ignoreCase = true)
        val isContentUriMedia = currentVideo.path.startsWith("content://", ignoreCase = true)
        if (!getIsStreamMedia() && !isSmbMedia && !isContentUriMedia && !java.io.File(currentVideo.path).exists()) {
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
            val mediaItemBuilder = MediaItem.Builder().setUri(currentVideo.path)
            if (subtitleUri != null) {
                // MIME type reflects the SUBTITLE FILE'S actual format
                // rather than always claiming SubRip — files this app's
                // own sync/clean/dual pipeline generates are always
                // genuine SRT regardless of the original source format,
                // since those pipelines only operate on SRT text, so they
                // still correctly report as SRT here.
                val detectedFormat = detectSubtitleFormat(subtitleUri)
                val subtitleMimeType = detectedFormat.mimeType ?: MimeTypes.APPLICATION_SUBRIP
                mediaItemBuilder.setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(subtitleMimeType).setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                ))
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.seekTo(resumePosition.coerceAtLeast(0L))
            exoPlayer.playWhenReady = true; exoPlayer.play()
            exoPlayer.playbackParameters = PlaybackParameters(getPlaybackSpeed())
            setIsVideoEnded(false)
        } catch (e: Exception) {
            setPlayerErrorMessage("Couldn't start playback: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
