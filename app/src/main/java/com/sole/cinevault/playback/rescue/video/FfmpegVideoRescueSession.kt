package com.sole.cinevault.playback.rescue.video

enum class FfmpegVideoRescueSessionState { NEW, READY, PLAYING, PAUSED, STOPPED, RELEASED }

class FfmpegVideoRescueSession(val runtime: FfmpegVideoRescueRuntime) {
    var state = FfmpegVideoRescueSessionState.NEW
        private set
    var generation: FfmpegVideoDecodeGeneration? = null
        private set

    fun prepare(startPositionMs: Long = 0L): FfmpegVideoDecodeGeneration {
        check(state == FfmpegVideoRescueSessionState.NEW) { "prepare requires NEW state" }
        val position = startPositionMs.coerceAtLeast(0L)
        runtime.createNativeBridge()
        runtime.eventRouter.resetTerminalState()
        runtime.clock.start(position)
        return runtime.decoderController.prepare(position).also {
            generation = it
            state = FfmpegVideoRescueSessionState.READY
        }
    }

    fun play() {
        check(state == FfmpegVideoRescueSessionState.READY || state == FfmpegVideoRescueSessionState.PAUSED) { "play requires READY or PAUSED state" }
        runtime.decoderController.play()
        runtime.clock.resume()
        state = FfmpegVideoRescueSessionState.PLAYING
    }

    // Compatibility entry point already used by the rescue handoff/controller.
    fun resume() = play()

    fun pause() {
        check(state == FfmpegVideoRescueSessionState.PLAYING) { "pause requires PLAYING state" }
        runtime.decoderController.pause()
        runtime.clock.pause()
        state = FfmpegVideoRescueSessionState.PAUSED
    }

    fun seekTo(positionMs: Long): FfmpegVideoDecodeGeneration {
        check(state == FfmpegVideoRescueSessionState.READY || state == FfmpegVideoRescueSessionState.PLAYING || state == FfmpegVideoRescueSessionState.PAUSED) { "seek requires an active prepared session" }
        val target = positionMs.coerceAtLeast(0L)
        runtime.framePump.flush()
        runtime.timeline.onExplicitSeek(target)
        runtime.eventRouter.resetTerminalState()
        return runtime.decoderController.seekTo(target).also { generation = it }
    }

    fun stop() {
        if (state == FfmpegVideoRescueSessionState.STOPPED || state == FfmpegVideoRescueSessionState.RELEASED) return
        check(state != FfmpegVideoRescueSessionState.NEW) { "stop requires a prepared session" }
        runtime.framePump.flush()
        runtime.decoderController.stop()
        runtime.clock.pause()
        state = FfmpegVideoRescueSessionState.STOPPED
    }

    fun release() {
        if (state == FfmpegVideoRescueSessionState.RELEASED) return
        runtime.release()
        generation = null
        state = FfmpegVideoRescueSessionState.RELEASED
    }
}
