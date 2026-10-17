package com.sole.cinevault.playback.rescue.video

/**
 * Worker-confined native audio decoder. Outputs interleaved signed 16-bit
 * stereo PCM at 48 kHz for Android AudioTrack.
 *
 * Native methods must never be called concurrently with close().
 */
internal class FfmpegNativeAudioDecoder private constructor(private var handle: Long) : AutoCloseable {
    fun tracks(): List<FfmpegNativeAudioTrack> {
        check(handle != 0L)
        return FfmpegNativeAudioTracks.decode(nativeTracks(handle))
    }

    fun selectedStreamIndex(): Int {
        check(handle != 0L)
        return nativeSelectedStream(handle)
    }

    fun durationMs(): Long {
        check(handle != 0L)
        return nativeDurationMs(handle)
    }

    fun nextPcm(): ByteArray? {
        check(handle != 0L)
        return nativeNext(handle)
    }

    fun seekTo(positionMs: Long) {
        require(positionMs >= 0)
        check(handle != 0L)
        nativeSeek(handle, positionMs)
    }

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeOpen(path: String, streamIndex: Int): Long
    private external fun nativeTracks(handle: Long): Array<String>
    private external fun nativeSelectedStream(handle: Long): Int
    private external fun nativeDurationMs(handle: Long): Long
    private external fun nativeNext(handle: Long): ByteArray?
    private external fun nativeSeek(handle: Long, positionMs: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        fun open(path: String, streamIndex: Int = -1): FfmpegNativeAudioDecoder {
            require(path.isNotBlank())
            check(FfmpegContainerProbeLoader.isAvailable())
            val decoder = FfmpegNativeAudioDecoder(0L)
            decoder.handle = decoder.nativeOpen(path, streamIndex)
            check(decoder.handle != 0L)
            return decoder
        }
    }
}
