package com.sole.cinevault.playback.rescue.video

/**
 * Owns exactly one live FFmpeg rescue session.
 *
 * Replacing a session always releases the previous session first so two rescue
 * decoders cannot own playback resources at the same time.
 */
class FfmpegVideoRescueSessionController {

    private var activeSession: FfmpegVideoRescueSession? = null

    val hasActiveSession: Boolean
        get() = activeSession != null &&
            activeSession?.state != FfmpegVideoRescueSessionState.RELEASED

    val state: FfmpegVideoRescueSessionState?
        get() = activeSession?.state

    fun attach(session: FfmpegVideoRescueSession) {
        if (activeSession === session) return
        activeSession?.release()
        activeSession = session
    }

    fun pause() {
        activeSession
            ?.takeUnless { it.state == FfmpegVideoRescueSessionState.RELEASED }
            ?.pause()
    }

    fun resume() {
        activeSession
            ?.takeUnless { it.state == FfmpegVideoRescueSessionState.RELEASED }
            ?.resume()
    }

    fun seekTo(positionMs: Long) {
        activeSession
            ?.takeUnless { it.state == FfmpegVideoRescueSessionState.RELEASED }
            ?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        activeSession?.release()
        activeSession = null
    }
}
