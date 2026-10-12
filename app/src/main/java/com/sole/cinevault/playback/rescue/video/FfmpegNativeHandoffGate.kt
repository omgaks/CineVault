package com.sole.cinevault.playback.rescue.video

/**
 * Prevents video-only native rescue from claiming a malformed-container error
 * before audio and player controls have a working ownership handoff.
 *
 * Readiness must be supplied by the runtime that actually owns those systems,
 * never inferred from FFmpeg library availability alone.
 */
internal object FfmpegNativeHandoffGate {
    fun canActivate(
        eligible: Boolean,
        media3SurfaceAvailable: Boolean,
        audioHandoffReady: Boolean,
        controlsHandoffReady: Boolean,
        nativeWorkerRunning: Boolean,
    ): Boolean =
        eligible &&
            media3SurfaceAvailable &&
            audioHandoffReady &&
            controlsHandoffReady &&
            !nativeWorkerRunning
}
