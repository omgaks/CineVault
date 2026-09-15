package com.sole.cinevault

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PlayerPlaybackRecoveryState {
    var engineMode by mutableStateOf(PlaybackEngineMode.HARDWARE)
    var softwareFallbackAvailable by mutableStateOf(false)
    var softwareFallbackRequested by mutableStateOf(false)
    var fallbackResumePositionMs by mutableStateOf(0L)
    var fallbackErrorCode by mutableIntStateOf(0)
    var fallbackSubtitleUri by mutableStateOf<Uri?>(null)
    var activeVideoDecoderStatus by mutableStateOf(ActiveVideoDecoderStatus())
    var activeAudioDecoderStatus by mutableStateOf(ActiveAudioDecoderStatus())
    var audioFfmpegRescueAttempted by mutableStateOf(false)
        private set
    var fallbackReason by mutableStateOf<PlaybackFallbackReason?>(null)
    var fallbackOccurred by mutableStateOf(false)
    var droppedFrameUnhealthyStreak by mutableIntStateOf(0)
    var totalDroppedVideoFrames by mutableIntStateOf(0)
    var startupPlaybackConfirmed by mutableStateOf(false)
    var firstVideoFrameRendered by mutableStateOf(false)
    var lastFailureDiagnostic by mutableStateOf<PlaybackFailureDiagnostic?>(null)
    var failureHistory by mutableStateOf(emptyList<PlaybackFailureHistoryEntry>())

    var videoDecoderCapabilityReport by mutableStateOf<VideoDecoderCapabilityReport?>(null)
    var nativeVideoPlaybackReadiness by mutableStateOf(NativeVideoPlaybackReadiness.UNKNOWN)
    var streamInventory by mutableStateOf(PlaybackStreamInventory())

    val videoStreamProfile: VideoStreamProfile?
        get() = videoDecoderCapabilityReport?.streamProfile

    val videoPlaybackCompatibilityAssessment: VideoPlaybackCompatibilityAssessment
        get() = assessVideoPlaybackCompatibility(videoDecoderCapabilityReport)

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
        activeAudioDecoderStatus = ActiveAudioDecoderStatus()
        audioFfmpegRescueAttempted = false
        fallbackReason = null
        fallbackOccurred = false
        droppedFrameUnhealthyStreak = 0
        totalDroppedVideoFrames = 0
        startupPlaybackConfirmed = false
        firstVideoFrameRendered = false
        lastFailureDiagnostic = null
        failureHistory = emptyList()
        videoDecoderCapabilityReport = null
        nativeVideoPlaybackReadiness = NativeVideoPlaybackReadiness.UNKNOWN
        streamInventory = PlaybackStreamInventory()
    }

    fun updateVideoDecoderCapability(report: VideoDecoderCapabilityReport?) {
        videoDecoderCapabilityReport = report
        nativeVideoPlaybackReadiness = decideNativeVideoPlaybackReadiness(report)
        softwareFallbackAvailable = isPlatformSoftwareVideoFallbackAvailable(report)
    }

    fun updateStreamInventory(inventory: PlaybackStreamInventory) {
        streamInventory = inventory
    }

    fun recordFailureDiagnostic(diagnostic: PlaybackFailureDiagnostic) {
        lastFailureDiagnostic = diagnostic
        failureHistory = appendPlaybackFailureHistory(
            history = failureHistory,
            diagnostic = diagnostic,
        )
    }

    fun decideAudioRecovery(
        attribution: PlaybackFailureAttribution,
    ): AudioPlaybackRecoveryDecision = decideAudioPlaybackRecovery(
        attribution = attribution,
        selectedAudio = streamInventory.selectedAudio,
        activeDecoder = activeAudioDecoderStatus,
        ffmpegRescueAlreadyAttempted = audioFfmpegRescueAttempted,
    )

    fun markAudioFfmpegRescueAttempted() {
        audioFfmpegRescueAttempted = true
    }

    val streamRoutingPlan: PlaybackStreamRoutingPlan
        get() = buildPlaybackStreamRoutingPlan(
            inventory = streamInventory,
            engineMode = engineMode,
            videoCapabilityReport = videoDecoderCapabilityReport,
        )

    fun requestSoftwareFallback(errorCode: Int, resumePositionMs: Long, subtitleUri: Uri?) {
        fallbackErrorCode = errorCode
        fallbackReason = playbackFallbackReasonForErrorCode(errorCode)
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun requestProactiveSoftwareFallback(resumePositionMs: Long, subtitleUri: Uri?) {
        if (engineMode != PlaybackEngineMode.HARDWARE || fallbackOccurred ||
            softwareFallbackRequested || !softwareFallbackAvailable) return
        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.NATIVE_DECODER_UNAVAILABLE
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun confirmPlaybackStarted() { startupPlaybackConfirmed = true }
    fun confirmFirstVideoFrameRendered() { firstVideoFrameRendered = true }

    fun requestMissingFirstFrameFallback(resumePositionMs: Long, subtitleUri: Uri?) {
        if (engineMode != PlaybackEngineMode.HARDWARE || !softwareFallbackAvailable ||
            fallbackOccurred || softwareFallbackRequested || firstVideoFrameRendered) return
        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.FIRST_VIDEO_FRAME_MISSING
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun requestStartupStallFallback(resumePositionMs: Long, subtitleUri: Uri?) {
        if (engineMode != PlaybackEngineMode.HARDWARE || !softwareFallbackAvailable ||
            fallbackOccurred || softwareFallbackRequested || startupPlaybackConfirmed) return
        fallbackErrorCode = 0
        fallbackReason = PlaybackFallbackReason.STARTUP_STALLED
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
        fallbackSubtitleUri = subtitleUri
        softwareFallbackRequested = true
    }

    fun onDroppedVideoFrames(
        droppedFrames: Int, elapsedMs: Long, resumePositionMs: Long, subtitleUri: Uri?,
    ) {
        totalDroppedVideoFrames = (totalDroppedVideoFrames.toLong() +
            droppedFrames.coerceAtLeast(0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val thresholds = playbackHealthThresholdsFor(videoPlaybackCompatibilityAssessment)
        val health = assessDroppedFrameHealth(droppedFrames, elapsedMs, thresholds)
        droppedFrameUnhealthyStreak = nextDroppedFrameUnhealthyStreak(
            droppedFrameUnhealthyStreak, health
        )
        if (shouldFallbackForDroppedFrames(
                droppedFrameUnhealthyStreak, engineMode, softwareFallbackAvailable,
                thresholds.requiredUnhealthyDroppedFrameWindows
            ) && !fallbackOccurred && !softwareFallbackRequested) {
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
