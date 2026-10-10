package com.sole.cinevault.playback.rescue.video

/**
 * Safe optional loader for a separately provisioned FFmpeg native container
 * probe. A missing library is never advertised as a working rescue backend.
 */
internal object FfmpegContainerProbeLoader {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("cinevault_container_probe")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun isAvailable(): Boolean = loaded

    /**
     * One-frame RGBA bridge for rendering experiments. Never call on the UI
     * thread; this is a synchronous bounded decode and not a video player.
     */
    /**
     * Bounded RGBA batch with frame timestamps. Missing entries indicate EOF.
     * The native decoder is reopened per call; this is not streaming playback.
     */
    fun decodeRgbaFrameBatch(path: String, maxFrames: Int): Result<List<TimedRgbaFrame>> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        require(maxFrames in 1..8) { "maxFrames must be 1..8" }
        check(loaded) { "Native FFmpeg decoder not installed" }
        val items = FfmpegContainerProbe().decodeRgbaFrameBatch(path, maxFrames)
        buildList {
            for (index in 0 until maxFrames) {
                val metadata = items[index * 2] as? LongArray ?: break
                val pixels = items[index * 2 + 1] as? ByteArray
                    ?: error("Missing frame pixels")
                require(metadata.size == 3)
                val width = metadata[0].toInt()
                val height = metadata[1].toInt()
                require(width > 0 && height > 0)
                require(pixels.size.toLong() == width.toLong() * height * 4L)
                add(TimedRgbaFrame(metadata[2], RgbaFrame(width, height, pixels)))
            }
        }
    }

    data class TimedRgbaFrame(val presentationTimeMs: Long, val image: RgbaFrame)

    fun decodeFirstRgbaFrame(path: String): Result<RgbaFrame> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        check(loaded) { "Native FFmpeg decoder not installed" }
        val dimensions = IntArray(2)
        val pixels = FfmpegContainerProbe().decodeFirstRgbaFrame(path, dimensions)
        require(dimensions[0] > 0 && dimensions[1] > 0)
        require(pixels.size.toLong() == dimensions[0].toLong() * dimensions[1] * 4)
        RgbaFrame(dimensions[0], dimensions[1], pixels)
    }

    data class RgbaFrame(val width: Int, val height: Int, val rgba: ByteArray)

    fun decodeVideoFrames(path: String, maxFrames: Int): Result<String> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        require(maxFrames in 1..300) { "maxFrames must be between 1 and 300" }
        check(loaded) { "Native FFmpeg decoder not installed" }
        FfmpegContainerProbe().decodeVideoFrames(path, maxFrames)
    }

    fun decodeFirstVideoFrame(path: String): Result<String> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        check(loaded) { "Native FFmpeg decoder not installed" }
        FfmpegContainerProbe().decodeFirstVideoFrame(path)
    }

    fun probeLocalFile(path: String): Result<String> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        check(loaded) { "Native FFmpeg container probe not installed" }
        FfmpegContainerProbe().probeLocalFile(path)
    }
}
