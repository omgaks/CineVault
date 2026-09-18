package com.sole.cinevault.playback.rescue.video

class FfmpegVideoRescueSessionOwner {
    var session: FfmpegVideoRescueSession? = null
        private set

    fun install(runtime: FfmpegVideoRescueRuntime): FfmpegVideoRescueSession {
        session?.release()
        return FfmpegVideoRescueSession(runtime).also { session = it }
    }

    fun clear() {
        session?.release()
        session = null
    }
}
