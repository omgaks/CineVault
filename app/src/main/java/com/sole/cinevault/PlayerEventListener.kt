package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
    playbackEngineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    audioLanguageCheckedForPath: String?,
    onAudioLanguageCheckedForPathChanged: (String?) -> Unit,
    onVideoDecoderCapabilityReportChanged: (VideoDecoderCapabilityReport?) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onErrorRetryCountChanged: (Int) -> Unit,
    onPlayerErrorMessageChanged: (String?) -> Unit,
    onVideoEndedChanged: (Boolean) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onQueueNextEpisode: (VideoWithMetadata) -> Unit,
    onAdvanceImmediately: (VideoWithMetadata) -> Unit,
    onShowControls: () -> Unit,
    onRetryPlayback: (subtitleUri: android.net.Uri?, resumePosition: Long) -> Unit,
    onSoftwareFallbackRequested: (
        errorCode: Int,
        resumePosition: Long,
        subtitleUri: android.net.Uri?,
    ) -> Unit,
) {
    DisposableEffect(
        player,
        currentVideoPath,
        currentMediaType,
        isStreamMedia,
        episodeList,
        autoPlayEnabled,
        errorRetryCount,
        playbackEngineMode,
        softwareFallbackAvailable,
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

            override fun onTracksChanged(tracks: Tracks) {
                val selectedVideoFormat = tracks.groups
                    .firstOrNull { group ->
                        group.type == C.TRACK_TYPE_VIDEO && group.isSelected
                    }
                    ?.let { group ->
                        (0 until group.length)
                            .firstOrNull { index -> group.isTrackSelected(index) }
                            ?.let { index -> group.getTrackFormat(index) }
                    }

                val capabilityReport = selectedVideoFormat?.let {
                    inspectVideoDecoderCapability(it)
                }

                onVideoDecoderCapabilityReportChanged(capabilityReport)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                val positionAtError = player.currentPosition.coerceAtLeast(0L)

                // Playback Resilience Slice 72:
                // Route every playback failure through the tested recovery policy.
                //
                // A real software VIDEO engine is not wired yet, so fallback
                // availability intentionally remains false in this slice. This
                // preserves today's behavior while replacing the old ad-hoc
                // retry branch with the same decision engine that the software
                // fallback path will use in the next slices.
                val recovery = decidePlaybackRecovery(
                    errorCode = error.errorCode,
                    currentRetryCount = errorRetryCount,
                    engineMode = playbackEngineMode,
                    softwareFallbackAvailable = softwareFallbackAvailable,
                )

                when (recovery.action) {
                    PlaybackRecoveryAction.RETRY_CURRENT -> {
                        onErrorRetryCountChanged(recovery.nextRetryCount)
                        scope.launch {
                            delay(1000L * recovery.nextRetryCount)
                            onRetryPlayback(trackUi.originalUri, positionAtError)
                        }
                    }

                    PlaybackRecoveryAction.SWITCH_TO_SOFTWARE -> {
                        onSoftwareFallbackRequested(
                            error.errorCode,
                            positionAtError,
                            trackUi.originalUri,
                        )
                    }

                    PlaybackRecoveryAction.FAIL -> {
                        onPlayerErrorMessageChanged(friendlyPlaybackError(error))
                        onPlayingChanged(false)
                    }
                }
            }
        }

        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
