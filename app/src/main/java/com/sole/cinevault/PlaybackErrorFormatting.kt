package com.sole.cinevault

import androidx.media3.common.PlaybackException

fun findHttpStatusDetail(error: Throwable): String? {
    var cause: Throwable? = error
    var depth = 0

    while (cause != null && depth < 10) {
        if (
            cause is androidx.media3.datasource.HttpDataSource
                .InvalidResponseCodeException
        ) {
            val message = cause.responseMessage
            return if (!message.isNullOrBlank()) {
                "${cause.responseCode} $message"
            } else {
                "${cause.responseCode}"
            }
        }

        cause = cause.cause
        depth++
    }

    return null
}

fun friendlyPlaybackError(error: PlaybackException): String {
    return friendlyPlaybackErrorForCode(
        errorCode = error.errorCode,
        errorCodeName = error.errorCodeName,
        originalMessage = error.message,
        causeMessage = error.cause?.message,
        httpStatusDetail = findHttpStatusDetail(error),
    )
}

internal fun friendlyPlaybackErrorForCode(
    errorCode: Int,
    errorCodeName: String,
    originalMessage: String? = null,
    causeMessage: String? = null,
    httpStatusDetail: String? = null,
): String {
    val detail = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "File not found. It may have been moved, renamed, or the drive " +
                "it's on was disconnected."

        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Permission denied reading this file."

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Connection problem reading this file. If it's on a USB drive " +
                "or network share, check the connection."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The server rejected the request for this stream." +
                (httpStatusDetail?.let { " (HTTP $it)" } ?: "")

        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "The server returned this link as something other than a " +
                "playable video (wrong content type)."

        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            "This is an unencrypted (http://) link — Android blocks plain " +
                "http streams by default. Try an https:// link instead."

        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
            "Couldn't read this stream at the expected position — the file " +
                "may be shorter than reported or still uploading."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This link's format isn't a container CineVault recognizes."

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This device can't decode this file's video or audio format."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "This file appears to be corrupted or incomplete."

        PlaybackException.ERROR_CODE_TIMEOUT ->
            "Timed out trying to start playback."

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
            val causeDetail = causeMessage?.takeIf { it.isNotBlank() }

            if (causeDetail != null) {
                "Couldn't read this source: $causeDetail"
            } else {
                "Couldn't read this source (unspecified I/O error)."
            }
        }

        else -> originalMessage ?: "Playback error"
    }

    return "$detail ($errorCodeName)"
}

fun isTransientPlaybackError(error: PlaybackException): Boolean {
    return isTransientPlaybackErrorCode(error.errorCode)
}

internal fun isTransientPlaybackErrorCode(errorCode: Int): Boolean {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true

        else -> false
    }
}
