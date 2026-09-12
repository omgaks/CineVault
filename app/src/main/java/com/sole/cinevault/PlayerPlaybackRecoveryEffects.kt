package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Slice 86: single host for Playback Resilience runtime signals/effects.
 *
 * The screen still owns the player and state holders. This host owns:
 * - actual decoder analytics
 * - dropped-frame recovery signal
 * - first-frame / black-video detection
 * - startup-stall detection
 * - proactive software fallback
 * - consuming a software fallback request and resuming playback
 *
 * Keeping these together prevents VideoPlayerScreen from growing one
 * LaunchedEffect per new recovery signal.
 */
@Composable
internal fun PlayerPlaybackRecoveryEffects(
    player: ExoPlayer,
    decoderSelector: RecoveryAwareMediaCodecSelector,
    recoveryState: PlayerPlaybackRecoveryState,
    playbackHealth: PlayerPlaybackHealthState,
    currentVideoPath: String,
    isPlaying: Boolean,
    subtitleUri: Uri?,
    onPlayCurrentVideoWithSubtitle: (
        subtitleUri: Uri?,
        resumePosition: Long,
        isOriginalSubtitle: Boolean,
    ) -> Unit,
) {
    val latestSubtitleUri by rememberUpdatedState(subtitleUri)
    val latestPlayCurrentVideoWithSubtitle by rememberUpdatedState(
        onPlayCurrentVideoWithSubtitle
    )

    PlayerDecoderAnalytics(
        player = player,
        capabilityReport = recoveryState.videoDecoderCapabilityReport,
        engineMode = recoveryState.engineMode,
        onDecoderStatusChanged = {
            recoveryState.activeVideoDecoderStatus = it
        },
        onDroppedVideoFrames = { droppedFrames, elapsedMs ->
            recoveryState.onDroppedVideoFrames(
                droppedFrames = droppedFrames,
                elapsedMs = elapsedMs,
                resumePositionMs = player.currentPosition,
                subtitleUri = latestSubtitleUri,
            )
        },
        onFirstVideoFrameRendered = {
            recoveryState.confirmFirstVideoFrameRendered()
        },
    )

    // Proactive path: exact stream has no fully-supported hardware decoder
    // but does have a supported software-only decoder.
    LaunchedEffect(
        recoveryState.nativeVideoPlaybackReadiness,
        recoveryState.softwareFallbackAvailable,
        recoveryState.engineMode,
        currentVideoPath,
    ) {
        if (
            recoveryState.nativeVideoPlaybackReadiness ==
                NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED &&
            recoveryState.softwareFallbackAvailable &&
            recoveryState.engineMode == PlaybackEngineMode.HARDWARE &&
            !recoveryState.fallbackOccurred &&
            !recoveryState.softwareFallbackRequested
        ) {
            recoveryState.requestProactiveSoftwareFallback(
                resumePositionMs = player.currentPosition,
                subtitleUri = latestSubtitleUri,
            )
        }
    }

    // Black-video path: playback position is advancing but no first frame
    // appears within the health window.
    LaunchedEffect(
        isPlaying,
        recoveryState.firstVideoFrameRendered,
        recoveryState.engineMode,
        recoveryState.softwareFallbackAvailable,
        recoveryState.videoDecoderCapabilityReport,
        currentVideoPath,
    ) {
        if (
            !isPlaying ||
            recoveryState.firstVideoFrameRendered ||
            recoveryState.videoDecoderCapabilityReport == null ||
            recoveryState.engineMode != PlaybackEngineMode.HARDWARE ||
            !recoveryState.softwareFallbackAvailable ||
            recoveryState.fallbackOccurred ||
            recoveryState.softwareFallbackRequested
        ) {
            return@LaunchedEffect
        }

        val windowStartPosition = player.currentPosition
        delay(8_000L)

        val playbackProgressMs =
            abs(player.currentPosition - windowStartPosition)

        if (
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = isPlaying,
                hasSelectedVideoTrack =
                    recoveryState.videoDecoderCapabilityReport != null,
                firstVideoFrameRendered =
                    recoveryState.firstVideoFrameRendered,
                elapsedMs = 8_000L,
                playbackProgressMs = playbackProgressMs,
                engineMode = recoveryState.engineMode,
                softwareFallbackAvailable =
                    recoveryState.softwareFallbackAvailable,
                fallbackOccurred = recoveryState.fallbackOccurred,
            ) &&
            !recoveryState.softwareFallbackRequested
        ) {
            recoveryState.requestMissingFirstFrameFallback(
                resumePositionMs = player.currentPosition,
                subtitleUri = latestSubtitleUri,
            )
        }
    }

    // Startup-stall path: decoder/player never reaches healthy playback.
    LaunchedEffect(
        playbackHealth.isBuffering,
        recoveryState.startupPlaybackConfirmed,
        recoveryState.engineMode,
        recoveryState.softwareFallbackAvailable,
        currentVideoPath,
    ) {
        if (
            !playbackHealth.isBuffering ||
            recoveryState.startupPlaybackConfirmed ||
            recoveryState.engineMode != PlaybackEngineMode.HARDWARE ||
            !recoveryState.softwareFallbackAvailable ||
            recoveryState.fallbackOccurred ||
            recoveryState.softwareFallbackRequested
        ) {
            return@LaunchedEffect
        }

        val windowStartPosition = player.currentPosition
        delay(12_000L)

        val playbackProgressMs =
            abs(player.currentPosition - windowStartPosition)

        if (
            shouldFallbackForStartupStall(
                isBuffering = playbackHealth.isBuffering,
                startupPlaybackConfirmed =
                    recoveryState.startupPlaybackConfirmed,
                elapsedMs = 12_000L,
                playbackProgressMs = playbackProgressMs,
                engineMode = recoveryState.engineMode,
                softwareFallbackAvailable =
                    recoveryState.softwareFallbackAvailable,
                fallbackOccurred = recoveryState.fallbackOccurred,
            ) &&
            !recoveryState.softwareFallbackRequested
        ) {
            recoveryState.requestStartupStallFallback(
                resumePositionMs = player.currentPosition,
                subtitleUri = latestSubtitleUri,
            )
        }
    }

    // Consume every recovery request through one guarded software transition.
    LaunchedEffect(
        recoveryState.softwareFallbackRequested,
        currentVideoPath,
    ) {
        if (!recoveryState.softwareFallbackRequested) {
            return@LaunchedEffect
        }

        val resumePosition = recoveryState.fallbackResumePositionMs
        val fallbackSubtitleUri = recoveryState.fallbackSubtitleUri

        decoderSelector.engineMode = PlaybackEngineMode.SOFTWARE
        recoveryState.activateSoftwareFallback()

        // A software rescue starts a new decoder attempt. Do not carry the
        // native decoder's transient retry/error UI into that attempt.
        playbackHealth.errorRetryCount = 0
        playbackHealth.playerErrorMessage = null

        latestPlayCurrentVideoWithSubtitle(
            fallbackSubtitleUri,
            resumePosition,
            false,
        )
    }
}
