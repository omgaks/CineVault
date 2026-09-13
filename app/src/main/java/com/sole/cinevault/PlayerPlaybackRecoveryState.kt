package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Per-video runtime state for Playback Resilience.
 *
 * The state is intentionally remembered with currentVideo.path by the screen,
 * so a fallback decision made for one title cannot leak into the next title.
 */
class PlayerPlaybackRecoveryState {
    var engineMode by mutableStateOf(PlaybackEngineMode.HARDWARE)

    /**
     * False until CineVault has a real software VIDEO engine that can accept
     * the current item. Slice 73 wires the state; a later slice turns this on
     * only when the fallback engine is actually available.
     */
    var softwareFallbackAvailable by mutableStateOf(false)

    var softwareFallbackRequested by mutableStateOf(false)
    var fallbackResumePositionMs by mutableStateOf(0L)
    var fallbackErrorCode by mutableIntStateOf(0)
    var fallbackSubtitleUri by mutableStateOf<Uri?>(null)
    var activeVideoDecoderStatus by mutableStateOf(ActiveVideoDecoderStatus())
    var fallbackReason by mutableStateOf<PlaybackFallbackReason?>(null)
    var fallbackOccurred by mutableStateOf(false)
    var droppedFrameUnhealthyStreak by mutableIntStateOf(0)
    var totalDroppedVideoFrames by mutableIntStateOf(0)
    var startupPlaybackConfirmed by mutableStateOf(false)
    var firstVideoFrameRendered by mutableStateOf(false)

    // Slice 76: capability report for the currently selected VIDEO track.
    // Because this entire holder is remembered per currentVideo.path, the
    // report cannot leak from one title/episode into another.
    var videoDecoderCapabilityReport by mutableStateOf<VideoDecoderCapabilityReport?>(null)
    var nativeVideoPlaybackReadiness by mutableStateOf(
        NativeVideoPlaybackReadiness.UNKNOWN
    )

    val videoStreamProfile: VideoStreamProfile?
        get() = videoDecoderCapabilityReport?.streamProfile

    val videoPlaybackCompatibilityAssessment: VideoPlaybackCompatibilityAssessment
        get() = assessVideoPlaybackCompatibility(
            videoDecoderCapabilityReport
        )

    val playbackDiagnosticsSnapshot: PlaybackDiagnosticsSnapshot
        get() = buildPlaybackDiagnosticsSnapshot(this)

    fun resetForNewVideo() {
        engineMode = PlaybackEngineMode.HARDWARE
        softwareFallbackAvailable = false
        softwareFallbackRequested = false
        fallbackResumePositionMs = 0L
        fallbackErrorCode = 0
        fallbackSubtitleUri = null
        activeVideoDecoderStatus = ActiveVideoDecoderStatus()
        fallbackReason = null
        fallbackOccurred = false
        droppedFrameUnhealthyStreak = 0
        totalDroppedVideoFrames = 0
        startupPlaybackConfirmed = false
        firstVideoFrameRendered = false
        videoDecoderCapabilityReport = null
        nativeVideoPlaybackReadiness = NativeVideoPlaybackReadiness.UNKNOWN
    }

    fun updateVideoDecoderCapability(
        report: VideoDecoderCapabilityReport?,
    ) {
        videoDecoderCapabilityReport = report
        nativeVideoPlaybackReadiness = decideNativeVideoPlaybackReadiness(report)
        softwareFallbackAvailable = isPlatformSoftwareVideoFallbackAvailable(report)
    }

    fun requestSoftwareFallback(
        errorCode: Int,
        resumePositionMs: Long,
        subtitleUri: Uri?,
    ) {
        fallbackErrorCode = errorCode
        fallbackReason = playbackFallbackReasonForErrorCode(errorCode)
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun requestProactiveSoftwareFallback(
        resumePositionMs: Long,
        subtitleUri: Uri?,
    ) {
        if (
            engineMode != PlaybackEngineMode.HARDWARE ||
            fallbackOccurred ||
            softwareFallbackRequested ||
            !softwareFallbackAvailable
        ) {
            return
        }

        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.NATIVE_DECODER_UNAVAILABLE
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun confirmPlaybackStarted() {
        startupPlaybackConfirmed = true
    }

    fun confirmFirstVideoFrameRendered() {
        firstVideoFrameRendered = true
    }

    fun requestMissingFirstFrameFallback(
        resumePositionMs: Long,
        subtitleUri: Uri?,
    ) {
        if (
            engineMode != PlaybackEngineMode.HARDWARE ||
            !softwareFallbackAvailable ||
            fallbackOccurred ||
            softwareFallbackRequested ||
            firstVideoFrameRendered
        ) {
            return
        }

        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.FIRST_VIDEO_FRAME_MISSING
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun requestStartupStallFallback(
        resumePositionMs: Long,
        subtitleUri: Uri?,
    ) {
        if (
            engineMode != PlaybackEngineMode.HARDWARE ||
            !softwareFallbackAvailable ||
            fallbackOccurred ||
            softwareFallbackRequested ||
            startupPlaybackConfirmed
        ) {
            return
        }

        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.STARTUP_STALLED
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun onDroppedVideoFrames(
        droppedFrames: Int,
        elapsedMs: Long,
        resumePositionMs: Long,
        subtitleUri: Uri?,
    ) {
        totalDroppedVideoFrames = (
            totalDroppedVideoFrames.toLong() +
                droppedFrames.coerceAtLeast(0).toLong()
        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val thresholds = playbackHealthThresholdsFor(
            videoPlaybackCompatibilityAssessment
        )

        val health = assessDroppedFrameHealth(
            droppedFrames = droppedFrames,
            elapsedMs = elapsedMs,
            thresholds = thresholds,
        )

        droppedFrameUnhealthyStreak = nextDroppedFrameUnhealthyStreak(
            currentStreak = droppedFrameUnhealthyStreak,
            health = health,
        )

        if (
            shouldFallbackForDroppedFrames(
                unhealthyStreak = droppedFrameUnhealthyStreak,
                engineMode = engineMode,
                softwareFallbackAvailable = softwareFallbackAvailable,
                requiredUnhealthyWindows =
                    thresholds.requiredUnhealthyDroppedFrameWindows,
            ) &&
            !fallbackOccurred &&
            !softwareFallbackRequested
        ) {
            fallbackErrorCode = 0
            fallbackReason = PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES
            fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
            fallbackSubtitleUri = subtitleUri
            softwareFallbackRequested = true
        }
    }

    fun activateSoftwareFallback() {
        engineMode = PlaybackEngineMode.SOFTWARE
        fallbackOccurred = true
        droppedFrameUnhealthyStreak = 0
        startupPlaybackConfirmed = false
        firstVideoFrameRendered = false
        softwareFallbackRequested = false
    }
}
