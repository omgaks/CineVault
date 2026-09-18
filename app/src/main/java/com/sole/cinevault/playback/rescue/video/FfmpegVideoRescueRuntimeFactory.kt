package com.sole.cinevault.playback.rescue.video

/**
 * Injectable factory boundary for owners of the rescue runtime.
 *
 * Keeping construction behind a factory will let B19/player integration own a
 * runtime without knowing its internal graph, and lets tests provide a native
 * bridge substitute without JNI.
 */
fun interface FfmpegVideoRescueRuntimeFactory {
    fun create(
        nativeBridge: FfmpegVideoNativeBridge,
        frameSink: FfmpegDecodedVideoFrameSink,
    ): FfmpegVideoRescueRuntime

    companion object {
        val DEFAULT = FfmpegVideoRescueRuntimeFactory { bridge, sink ->
            FfmpegVideoRescueRuntime.create(
                nativeBridge = bridge,
                frameSink = sink,
            )
        }
    }
}
