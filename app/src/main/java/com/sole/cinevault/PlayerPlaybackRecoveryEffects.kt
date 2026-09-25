package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

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
        onAudioDecoderStatusChanged = {
            recoveryState.activeAudioDecoderStatus = it
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
        ) return@LaunchedEffect

        val windowStartPosition = player.currentPosition
        delay(8_000L)
        val playbackProgressMs = abs(player.currentPosition - windowStartPosition)

        if (
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = isPlaying,
                hasSelectedVideoTrack = recoveryState.videoDecoderCapabilityReport != null,
                firstVideoFrameRendered = recoveryState.firstVideoFrameRendered,
                elapsedMs = 8_000L,
                playbackProgressMs = playbackProgressMs,
                engineMode = recoveryState.engineMode,
                softwareFallbackAvailable = recoveryState.softwareFallbackAvailable,
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
        ) return@LaunchedEffect

        val windowStartPosition = player.currentPosition
        delay(12_000L)
        val playbackProgressMs = abs(player.currentPosition - windowStartPosition)

        if (
            shouldFallbackForStartupStall(
                isBuffering = playbackHealth.isBuffering,
                startupPlaybackConfirmed = recoveryState.startupPlaybackConfirmed,
                elapsedMs = 12_000L,
                playbackProgressMs = playbackProgressMs,
                engineMode = recoveryState.engineMode,
                softwareFallbackAvailable = recoveryState.softwareFallbackAvailable,
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

    // D14-S6: consume video rescue only through the unified runtime gate.
    LaunchedEffect(
        recoveryState.softwareFallbackRequested,
        recoveryState.softwareFallbackAvailable,
        recoveryState.engineMode,
        currentVideoPath,
    ) {
        if (!recoveryState.softwareFallbackRequested) {
            return@LaunchedEffect
        }
        if (!PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(recoveryState)) {
            return@LaunchedEffect
        }

        val resumePosition = recoveryState.fallbackResumePositionMs
        val fallbackSubtitleUri = recoveryState.fallbackSubtitleUri

        decoderSelector.engineMode = PlaybackEngineMode.SOFTWARE
        recoveryState.activateSoftwareFallback()

        playbackHealth.errorRetryCount = 0
        playbackHealth.playerErrorMessage = null

        latestPlayCurrentVideoWithSubtitle(
            fallbackSubtitleUri,
            resumePosition,
            false,
        )
    }
}
