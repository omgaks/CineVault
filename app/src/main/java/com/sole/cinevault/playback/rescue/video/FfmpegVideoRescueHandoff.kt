package com.sole.cinevault.playback.rescue.video

/**
 * Result of asking the rescue stack to take ownership of video playback.
 */
sealed interface FfmpegVideoRescueHandoffResult {
    data class Activated(
        val state: FfmpegVideoRescueSessionState,
    ) : FfmpegVideoRescueHandoffResult

    data class Rejected(
        val reason: String,
    ) : FfmpegVideoRescueHandoffResult
}

/**
 * Opens a verified rescue session and transfers ownership to [controller].
 *
 * The controller is only changed after a session has opened successfully, so a
 * failed FFmpeg attempt cannot destroy an already-running rescue session.
 */
fun handoffToFfmpegVideoRescue(
    backend: FfmpegVideoSessionBackend,
    controller: FfmpegVideoRescueSessionController,
    mediaUri: String,
    resumePositionMs: Long,
    playWhenReady: Boolean,
): FfmpegVideoRescueHandoffResult {
    return when (
        val result = openFfmpegVideoRescueSession(
            backend = backend,
            mediaUri = mediaUri,
            resumePositionMs = resumePositionMs,
            playWhenReady = playWhenReady,
        )
    ) {
        is FfmpegVideoSessionOpenResult.Opened -> {
            controller.attach(result.session)

            if (playWhenReady) {
                result.session.resume()
            } else {
                result.session.pause()
            }

            FfmpegVideoRescueHandoffResult.Activated(result.session.state)
        }

        is FfmpegVideoSessionOpenResult.Rejected ->
            FfmpegVideoRescueHandoffResult.Rejected(result.reason)
    }
}
