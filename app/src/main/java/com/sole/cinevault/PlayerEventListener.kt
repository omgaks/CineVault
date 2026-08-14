package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerEventListener(
    context: Context,
    scope: CoroutineScope,
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    currentVideoPath: String,
    currentMediaType: String,
    isStreamMedia: Boolean,
    episodeList: List<VideoWithMetadata>,
    autoPlayEnabled: Boolean,
    errorRetryCount: Int,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    audioLanguageCheckedForPath: String?,
    onAudioLanguageCheckedForPathChanged: (String?) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onErrorRetryCountChanged: (Int) -> Unit,
    onPlayerErrorMessageChanged: (String?) -> Unit,
    onVideoEndedChanged: (Boolean) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onQueueNextEpisode: (VideoWithMetadata) -> Unit,
    onAdvanceImmediately: (VideoWithMetadata) -> Unit,
    onShowControls: () -> Unit,
    onRetryPlayback: (subtitleUri: android.net.Uri?, resumePosition: Long) -> Unit,
) {
    DisposableEffect(
        player,
        currentVideoPath,
        currentMediaType,
        isStreamMedia,
        episodeList,
        autoPlayEnabled,
        errorRetryCount,
        audioLanguageCheckedForPath,
    ) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                onBufferingChanged(state == Player.STATE_BUFFERING)

                if (state == Player.STATE_READY) {
                    onErrorRetryCountChanged(0)
                    onPlayerErrorMessageChanged(null)
                    val realDuration = player.duration
                    if (realDuration > 0L && !isStreamMedia) {
                        savePlayerDuration(context, currentVideoPath, realDuration)
                    }

                    if (
                        coreUi.behaviorPrefs.disableWhenAudioMatchesPreferred &&
                        audioLanguageCheckedForPath != currentVideoPath
                    ) {
                        onAudioLanguageCheckedForPathChanged(currentVideoPath)
                        val audioLanguage = player.currentTracks.groups
                            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                            ?.let { group ->
                                (0 until group.length)
                                    .firstOrNull { group.isTrackSelected(it) }
                                    ?.let { index -> group.getTrackFormat(index).language }
                            }
                        val preferred = coreUi.behaviorPrefs.preferredLanguages.firstOrNull()
                        if (
                            audioLanguage != null && preferred != null &&
                            audioLanguage.take(2).equals(preferred.take(2), ignoreCase = true)
                        ) {
                            coreUi.subtitlesEnabled = false
                            trackSelector.parameters = trackSelector
                                .buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                        }
                    }
                }

                if (state == Player.STATE_ENDED) {
                    onVideoEndedChanged(true)
                    if (autoPlayEnabled && episodeList.isNotEmpty()) {
                        val index = episodeList.indexOfFirst { it.video.path == currentVideoPath }
                        val next = episodeList.getOrNull(index + 1)
                        if (next != null) {
                            if (currentMediaType.equals("tv", ignoreCase = true)) {
                                onQueueNextEpisode(next)
                            } else {
                                onAdvanceImmediately(next)
                            }
                        }
                    }
                    onShowControls()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                val positionAtError = player.currentPosition.coerceAtLeast(0L)
                if (isTransientPlaybackError(error) && errorRetryCount < 2) {
                    val nextRetryCount = errorRetryCount + 1
                    onErrorRetryCountChanged(nextRetryCount)
                    scope.launch {
                        delay(1000L * nextRetryCount)
                        onRetryPlayback(trackUi.originalUri, positionAtError)
                    }
                } else {
                    onPlayerErrorMessageChanged(friendlyPlaybackError(error))
                    onPlayingChanged(false)
                }
            }
        }

        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
