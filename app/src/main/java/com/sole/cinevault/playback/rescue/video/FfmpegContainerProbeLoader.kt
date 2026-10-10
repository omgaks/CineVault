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

    fun probeLocalFile(path: String): Result<String> = runCatching {
        require(path.isNotBlank()) { "File path is required" }
        check(loaded) { "Native FFmpeg container probe not installed" }
        FfmpegContainerProbe().probeLocalFile(path)
    }
}
