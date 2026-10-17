package com.sole.cinevault.playback.rescue.video

/**
 * Persistent FFmpeg decoder. Keeps demux and codec state across nextFrame()
 * calls. Call on a dedicated worker; never on the UI thread.
 *
 * This is a native video pipeline building block, not yet a player backend.
 */
internal class FfmpegNativeDecoderSession private constructor(
    private var handle: Long,
) : AutoCloseable {
    data class Frame(
        val presentationTimeMs: Long,
        val image: FfmpegContainerProbeLoader.RgbaFrame,
    )

    @Synchronized
    fun nextFrame(): Frame? {
        check(handle != 0L) { "Decoder session already closed" }
        val result = nativeNext(handle) ?: return null
        require(result.size == 2)
        val metadata = result[0] as LongArray
        val pixels = result[1] as ByteArray
        require(metadata.size == 3)
        val width = metadata[0].toInt()
        val height = metadata[1].toInt()
        require(width in 1..4096 && height in 1..4096)
        require(pixels.size.toLong() == width.toLong() * height * 4L)
        return Frame(metadata[2], FfmpegContainerProbeLoader.RgbaFrame(width, height, pixels))
    }

    /**
     * Seek the persistent decoder to a keyframe at or before targetMs.
     * The caller must discard previously decoded frames and reset its
     * presentation clock. Invoke from the decoder worker thread.
     */
    @Synchronized
    fun seekTo(targetMs: Long) {
        require(targetMs >= 0L) { "Seek position must be nonnegative" }
        check(handle != 0L) { "Decoder session already closed" }
        nativeSeek(handle, targetMs)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeOpen(path: String): Long
    private external fun nativeNext(handle: Long): Array<Any?>?
    private external fun nativeSeek(handle: Long, targetMs: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        fun open(path: String): FfmpegNativeDecoderSession {
            require(path.isNotBlank())
            check(FfmpegContainerProbeLoader.isAvailable()) { "Native FFmpeg is not installed" }
            val session = FfmpegNativeDecoderSession(0L)
            val id = session.nativeOpen(path)
            check(id != 0L) { "Unable to create FFmpeg decoder session" }
            session.handle = id
            return session
        }
    }
}
