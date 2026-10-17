package com.sole.cinevault.playback.rescue.video

/** AudioTrack's 32-bit frame counter is unsigned and wraps after long playback. */
internal class FfmpegNativeAudioClock {
    private var previousHead = 0L
    private var wraps = 0L
    private var baseMs = 0L
    var playedFrames = 0L
        private set

    fun reset(positionMs: Long) {
        require(positionMs >= 0L)
        previousHead = 0L
        wraps = 0L
        baseMs = positionMs
        playedFrames = 0L
    }

    fun positionMs(playbackHead: Int): Long {
        val head = playbackHead.toLong() and 0xffffffffL
        if (head < previousHead) wraps++
        previousHead = head
        playedFrames = (wraps shl 32) + head
        return baseMs + playedFrames * 1000L / 48000L
    }
}
