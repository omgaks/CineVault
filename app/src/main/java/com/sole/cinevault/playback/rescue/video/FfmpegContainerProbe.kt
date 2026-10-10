package com.sole.cinevault.playback.rescue.video

/**
 * Optional FFmpeg container probe. It is deliberately not a playback backend:
 * probing a damaged MKV does not guarantee packets can be decoded or rendered.
 *
 * The JNI library is not loaded unless the app is built with the separately
 * supplied and licensed native FFmpeg SDK.
 */
internal class FfmpegContainerProbe {
    external fun probeLocalFile(path: String): String
    external fun decodeFirstVideoFrame(path: String): String
    external fun decodeFirstRgbaFrame(path: String, dimensions: IntArray): ByteArray
    external fun decodeVideoFrames(path: String, maxFrames: Int): String
}
