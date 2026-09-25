package com.sole.cinevault

import android.net.Uri

data class AudioPlaybackRescueRequest(
    val errorCode: Int,
    val resumePositionMs: Long,
    val subtitleUri: Uri?,
    val rendererPreference: CineAudioRendererPreference =
        CineAudioRendererPreference.FFMPEG_FIRST,
)

/**
 * Converts an attributed audio failure into a one-shot FFmpeg-first runtime
 * rebuild request.
 *
 * D14-S5: the unified rescue runtime gate is now authoritative before we mark
 * the FFmpeg attempt. This prevents an already-active rescue renderer from
 * being admitted again while still allowing audio rescue during software-video
 * playback (the MIXED rescue case).
 *
 * Returning null means the failure is not eligible for audio rescue and must
 * continue through the existing generic recovery path.
 */
fun PlayerPlaybackRecoveryState.prepareAudioFfmpegRescue(
    attribution: PlaybackFailureAttribution,
    errorCode: Int,
    resumePositionMs: Long,
    subtitleUri: Uri?,
): AudioPlaybackRescueRequest? {
    val decision = decideAudioRecovery(attribution)
    if (decision.action != AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG) {
        return null
    }

    if (!PlaybackRescueRuntimeGate.shouldExecuteFfmpegAudio(this)) {
        return null
    }

    markAudioFfmpegRescueAttempted()

    return AudioPlaybackRescueRequest(
        errorCode = errorCode,
        resumePositionMs = resumePositionMs.coerceAtLeast(0L),
        subtitleUri = subtitleUri,
    )
}
