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
 * Media3 1.9.0 does not expose a rendererType field on ExoPlaybackException.
 * For renderer errors we derive the track type from rendererFormat.sampleMimeType
 * using a JVM-test-safe MIME prefix classifier, with rendererName as a
 * conservative fallback when the format is unavailable.
 */
fun attributePlaybackFailure(
    error: PlaybackException,
): PlaybackFailureAttribution {
    val exoError = error as? ExoPlaybackException
    val rendererFailure =
        exoError?.type == ExoPlaybackException.TYPE_RENDERER

    val rendererTrackType = if (rendererFailure) {
        inferRendererTrackType(
            sampleMimeType = exoError?.rendererFormat?.sampleMimeType,
            rendererName = exoError?.rendererName,
        )
    } else {
        null
    }

    return buildPlaybackFailureAttribution(
        rendererFailure = rendererFailure,
        rendererTrackType = rendererTrackType,
        errorCode = error.errorCode,
    )
}

fun inferRendererTrackType(
    sampleMimeType: String?,
    rendererName: String?,
): Int {
    val normalizedMime = sampleMimeType
        ?.trim()
        ?.lowercase()
        .orEmpty()

    when {
        normalizedMime.startsWith("video/") ->
            return C.TRACK_TYPE_VIDEO
        normalizedMime.startsWith("audio/") ->
            return C.TRACK_TYPE_AUDIO
        normalizedMime.startsWith("text/") ||
            normalizedMime.startsWith("application/") &&
                (
                    "subtitle" in normalizedMime ||
                        "subrip" in normalizedMime ||
                        "ttml" in normalizedMime ||
                        "cea" in normalizedMime
                ) ->
            return C.TRACK_TYPE_TEXT
    }

    val normalizedName = rendererName
        ?.trim()
        ?.lowercase()
        .orEmpty()

    return when {
        "video" in normalizedName -> C.TRACK_TYPE_VIDEO
        "audio" in normalizedName -> C.TRACK_TYPE_AUDIO
        "text" in normalizedName ||
            "subtitle" in normalizedName ->
            C.TRACK_TYPE_TEXT
        else -> C.TRACK_TYPE_UNKNOWN
    }
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
