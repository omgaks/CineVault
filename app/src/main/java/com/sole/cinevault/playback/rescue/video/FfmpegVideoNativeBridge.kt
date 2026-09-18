package com.sole.cinevault.playback.rescue.video

/**
 * Platform-neutral contract implemented by the future JNI/native FFmpeg layer.
 *
 * B17 deliberately defines the bridge without loading a native library or
 * declaring external JNI methods yet. That keeps the B-series testable on the
 * JVM and makes the native implementation replaceable behind one boundary.
 */
interface FfmpegVideoNativeBridge {
    val isCreated: Boolean

    fun create(
        eventListener: FfmpegVideoNativeEventListener,
    )

    fun prepare(
        generation: FfmpegVideoDecodeGeneration,
        startPositionMs: Long,
    )

    fun play()
    fun pause()

    fun seek(
        generation: FfmpegVideoDecodeGeneration,
        positionMs: Long,
    )

    fun stop()
    fun release()
}

/**
 * Callback boundary from the future native decoder into Kotlin.
 */
fun interface FfmpegVideoNativeEventListener {
    fun onEvent(event: FfmpegVideoDecodeEvent)
}
