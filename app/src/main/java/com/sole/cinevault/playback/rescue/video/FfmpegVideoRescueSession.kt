package com.sole.cinevault.playback.rescue.video

/**
 * Lifecycle contract for an FFmpeg video rescue session.
 *
 * B6 deliberately keeps native decoding behind this boundary. A later slice
 * can provide the real implementation without leaking FFmpeg lifecycle state
 * into VideoPlayerScreen.
 */
interface FfmpegVideoRescueSession {
    val state: FfmpegVideoRescueSessionState

    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()
}

enum class FfmpegVideoRescueSessionState {
    READY,
    PLAYING,
    PAUSED,
    RELEASED,
}
