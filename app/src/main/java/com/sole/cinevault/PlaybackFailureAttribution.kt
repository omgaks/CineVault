package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException

enum class PlaybackFailureStreamKind {
    VIDEO,
    AUDIO,
    TEXT,
    OTHER,
    UNKNOWN,
}

data class PlaybackFailureAttribution(
    val streamKind: PlaybackFailureStreamKind,
    val rendererFailure: Boolean,
    val rendererTrackType: Int?,
    val errorCode: Int,
) {
    val isAudioRendererFailure: Boolean
        get() =
            rendererFailure &&
                streamKind == PlaybackFailureStreamKind.AUDIO

    val isVideoRendererFailure: Boolean
        get() =
            rendererFailure &&
                streamKind == PlaybackFailureStreamKind.VIDEO
}

/**
 * Converts Media3's player error into a stream-level failure attribution.
 *
 * This does not decide recovery. It only answers which renderer lane failed,
 * so later recovery logic can avoid treating an audio decoder failure like a
 * video decoder failure.
 */
fun attributePlaybackFailure(
    error: PlaybackException,
): PlaybackFailureAttribution {
    val exoError = error as? ExoPlaybackException
    val rendererFailure =
        exoError?.type == ExoPlaybackException.TYPE_RENDERER
    val rendererTrackType =
        if (rendererFailure) exoError?.rendererType else null

    return buildPlaybackFailureAttribution(
        rendererFailure = rendererFailure,
        rendererTrackType = rendererTrackType,
        errorCode = error.errorCode,
    )
}

fun buildPlaybackFailureAttribution(
    rendererFailure: Boolean,
    rendererTrackType: Int?,
    errorCode: Int,
): PlaybackFailureAttribution {
    val streamKind = if (!rendererFailure) {
        PlaybackFailureStreamKind.UNKNOWN
    } else {
        when (rendererTrackType) {
            C.TRACK_TYPE_VIDEO ->
                PlaybackFailureStreamKind.VIDEO

            C.TRACK_TYPE_AUDIO ->
                PlaybackFailureStreamKind.AUDIO

            C.TRACK_TYPE_TEXT ->
                PlaybackFailureStreamKind.TEXT

            C.TRACK_TYPE_METADATA,
            C.TRACK_TYPE_CAMERA_MOTION,
            C.TRACK_TYPE_IMAGE ->
                PlaybackFailureStreamKind.OTHER

            else ->
                PlaybackFailureStreamKind.UNKNOWN
        }
    }

    return PlaybackFailureAttribution(
        streamKind = streamKind,
        rendererFailure = rendererFailure,
        rendererTrackType = rendererTrackType.takeIf { rendererFailure },
        errorCode = errorCode,
    )
}

fun playbackFailureStreamLabel(
    attribution: PlaybackFailureAttribution,
): String = when (attribution.streamKind) {
    PlaybackFailureStreamKind.VIDEO -> "Video renderer"
    PlaybackFailureStreamKind.AUDIO -> "Audio renderer"
    PlaybackFailureStreamKind.TEXT -> "Subtitle renderer"
    PlaybackFailureStreamKind.OTHER -> "Auxiliary renderer"
    PlaybackFailureStreamKind.UNKNOWN -> "Playback"
}
