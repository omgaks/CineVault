package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
    private val onPlayNext: (VideoWithMetadata) -> Unit,
    private val externalSubtitleSelector: ExternalSubtitleSelector? = null,
    private val subtitleOverlay: ExternalSubtitleOverlay? = null,
    private val trackSelector: DefaultTrackSelector? = null,
) {
    // True only when the current MediaItem carries an attached subtitle (the fallback path
    // for formats the overlay cannot draw). Such an item has to be rebuilt to remove it.
    private var playerItemHasSubtitle = false

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
        isOriginalSubtitle: Boolean = true,
        resetSubtitleTiming: Boolean = isOriginalSubtitle,
        forceRebuild: Boolean = false,
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
                trackUi.renderBaseUri = null
                if (resetSubtitleTiming) {
                    trackUi.appliedOffsetMs = 0L
                    coreUi.syncOffset = 0f
                }
            }

            val resumeAt = resumePosition.coerceAtLeast(0L)
            val overlay = subtitleOverlay
            val useOverlay = subtitleUri != null && overlay != null && overlay.canHandle(subtitleUri)

            // The video itself only needs rebuilding when it is not loaded yet, has stopped
            // (error / fallback), is a different file, or still carries an attached subtitle.
            // A plain subtitle change never touches the video: no pause, no buffering circle.
            val loadedUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            val needsRebuild =
                forceRebuild ||
                    exoPlayer.mediaItemCount == 0 ||
                    exoPlayer.playbackState == Player.STATE_IDLE ||
                    loadedUri != currentVideo.path ||
                    (playerItemHasSubtitle && (subtitleUri == null || useOverlay))

            when {
                useOverlay -> {
                    overlay!!.claimEarly()
                    if (needsRebuild) rebuildPlayer(currentVideo.path, null, resumeAt)
                    overlay.show(subtitleUri!!) { shown ->
                        if (!shown) {
                            // Unreadable by the overlay: let Media3 try with its own parser.
                            overlay.clear()
                            restoreTextSelection()
                            rebuildPlayer(
                                currentVideo.path,
                                subtitleUri,
                                exoPlayer.currentPosition.coerceAtLeast(0L),
                            )
                        }
                    }
                }
                subtitleUri != null -> {
                    // Overlay unavailable or format not supported (e.g. ASS): attach to the item.
                    overlay?.clear()
                    rebuildPlayer(currentVideo.path, subtitleUri, resumeAt)
                }
                else -> {
                    overlay?.clear()
                    if (needsRebuild) rebuildPlayer(currentVideo.path, null, resumeAt)
                }
            }
            exoPlayer.playbackParameters = PlaybackParameters(getPlaybackSpeed())
            setIsVideoEnded(false)
        } catch (e: Exception) {
            setPlayerErrorMessage("Couldn't start playback: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun rebuildPlayer(videoPath: String, subtitleUri: Uri?, resumeAt: Long) {
        val subtitleId = if (subtitleUri != null) externalSubtitleSelector?.nextId() else null
        val mediaItem = buildPlaybackMediaItem(
            videoPath,
            subtitleUri,
            trackUi.primaryLanguage,
            subtitleId ?: "cinevault-external",
        )
        exoPlayer.setMediaItem(mediaItem, resumeAt)
        exoPlayer.prepare()
        playerItemHasSubtitle = subtitleUri != null

        if (subtitleId != null && coreUi.subtitlesEnabled) {
            externalSubtitleSelector?.arm(subtitleId)
        } else {
            externalSubtitleSelector?.disarm()
        }

        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    /** Gives subtitle rendering back to ExoPlayer after the overlay could not read a file. */
    private fun restoreTextSelection() {
        trackSelector?.let { selector ->
            selector.parameters = selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !coreUi.subtitlesEnabled)
                .build()
        }
    }
}
