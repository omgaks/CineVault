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
    onStreamInventoryChanged: (PlaybackStreamInventory) -> Unit,
    onFailureDiagnostic: (PlaybackFailureDiagnostic) -> Unit = {},
    onAudioFailure: (
        attribution: PlaybackFailureAttribution,
        errorCode: Int,
        resumePosition: Long,
        subtitleUri: android.net.Uri?,
    ) -> Boolean = { _, _, _, _ -> false },
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
        player, currentVideoPath, currentMediaType, isStreamMedia, episodeList,
        autoPlayEnabled, errorRetryCount, playbackEngineMode,
        softwareFallbackAvailable, audioLanguageCheckedForPath,
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
                    if (coreUi.behaviorPrefs.disableWhenAudioMatchesPreferred &&
                        audioLanguageCheckedForPath != currentVideoPath) {
                        onAudioLanguageCheckedForPathChanged(currentVideoPath)
                        val audioLanguage = player.currentTracks.groups
                            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                            ?.let { group ->
                                (0 until group.length)
                                    .firstOrNull { group.isTrackSelected(it) }
                                    ?.let { index -> group.getTrackFormat(index).language }
                            }
                        val preferred = coreUi.behaviorPrefs.preferredLanguages.firstOrNull()
                        if (audioLanguage != null && preferred != null &&
                            audioLanguage.take(2).equals(preferred.take(2), ignoreCase = true)) {
                            coreUi.subtitlesEnabled = false
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        }
                    }
                }
                if (state == Player.STATE_ENDED) {
                    onVideoEndedChanged(true)
                    if (autoPlayEnabled && episodeList.isNotEmpty()) {
                        val index = episodeList.indexOfFirst { it.video.path == currentVideoPath }
                        val next = episodeList.getOrNull(index + 1)
                        if (next != null) {
                            if (currentMediaType.equals("tv", ignoreCase = true)) onQueueNextEpisode(next)
                            else onAdvanceImmediately(next)
                        }
                    }
                    onShowControls()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val inventory = inspectPlaybackStreamInventory(tracks)
                onStreamInventoryChanged(inventory)
                val selectedVideoFormat = tracks.groups
                    .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    ?.let { group ->
                        (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                            ?.let { group.getTrackFormat(it) }
                    }
                onVideoDecoderCapabilityReportChanged(
                    selectedVideoFormat?.let { inspectVideoDecoderCapability(it) }
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Picture enhancement runs on its own GPU pipeline. If that pipeline is what
                // failed, the Picture controller removes it and resumes playback here, so the
                // normal retry / software-fallback / error-screen logic must not run.
                if (com.sole.cinevault.picture.PictureEnhanceRegistry.current
                        ?.consumeEffectFailure(error) == true
                ) {
                    return
                }
                val positionAtError = player.currentPosition.coerceAtLeast(0L)
                val attribution = attributePlaybackFailure(error)

                // D15-R1: a bad/unsupported subtitle must not take the movie down
                // with it. Media3 reports subtitle decoder failures as renderer
                // errors too; recover only the TEXT renderer, keep video/audio,
                // and return the Tracks UI to an honest OFF state.
                if (shouldRecoverByDisablingSubtitles(attribution)) {
                    val resumePlayback = player.playWhenReady || player.isPlaying

                    coreUi.subtitlesEnabled = false
                    trackUi.selectedKey = "off"
                    trackUi.selectedLabel = ""
                    trackUi.selectedSource = ""

                    trackSelector.parameters = trackSelector
                        .buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()

                    onFailureDiagnostic(
                        buildPlaybackFailureDiagnostic(
                            attribution = attribution,
                            recovery = PlaybackRecoveryDecision(
                                action = PlaybackRecoveryAction.RETRY_CURRENT,
                                nextRetryCount = errorRetryCount,
                            ),
                        )
                    )

                    onPlayerErrorMessageChanged(
                        "This subtitle track couldn't be played. Subtitles were turned off."
                    )

                    // ExoPlayer is in STATE_IDLE after a playback error. prepare()
                    // recovers the existing media item; no media-item rebuild and
                    // therefore no deliberate video-surface replacement.
                    player.prepare()
                    player.seekTo(positionAtError)
                    player.playWhenReady = resumePlayback
                    if (resumePlayback) player.play()
                    onShowControls()
                    return
                }

                if (
                    attribution.isAudioRendererFailure &&
                    onAudioFailure(
                        attribution,
                        error.errorCode,
                        positionAtError,
                        trackUi.originalUri,
                    )
                ) {
                    return
                }

                val baseRecovery = decidePlaybackRecovery(
                    errorCode = error.errorCode,
                    currentRetryCount = errorRetryCount,
                    engineMode = playbackEngineMode,
                    softwareFallbackAvailable = softwareFallbackAvailable,
                )
                val recovery = routeRecoveryForFailure(
                    attribution = attribution,
                    recovery = baseRecovery,
                )

                onFailureDiagnostic(
                    buildPlaybackFailureDiagnostic(
                        attribution = attribution,
                        recovery = recovery,
                    )
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
                            error.errorCode, positionAtError, trackUi.originalUri,
                        )
                    }
                    PlaybackRecoveryAction.FAIL -> {
                        val streamPrefix = if (attribution.rendererFailure) {
                            "${playbackFailureStreamLabel(attribution)}: "
                        } else ""
                        onPlayerErrorMessageChanged(streamPrefix + friendlyPlaybackError(error))
                        onPlayingChanged(false)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
