package com.sole.cinevault.playback.rescue.video

enum class FfmpegVideoDecoderControlState {
    IDLE,
    PREPARED,
    PLAYING,
    PAUSED,
    STOPPED,
    RELEASED,
}

/**
 * Deterministic command/control boundary for the future native decoder.
 *
 * Generation is advanced on prepare and seek so callbacks from work started
 * before those boundaries are invalidated by the B14/B15 generation gate.
 */
class FfmpegVideoDecoderController(
    private val gate: FfmpegVideoDecodeGenerationGate,
    private val commandSink: FfmpegVideoDecoderCommandSink,
) {
    var state: FfmpegVideoDecoderControlState =
        FfmpegVideoDecoderControlState.IDLE
        private set

    fun prepare(startPositionMs: Long = 0L): FfmpegVideoDecodeGeneration {
        ensureNotReleased()
        val positionMs = startPositionMs.coerceAtLeast(0L)
        val generation = gate.advance()

        commandSink.send(
            FfmpegVideoDecoderCommand.Prepare(
                generation = generation,
                startPositionMs = positionMs,
            ),
        )
        state = FfmpegVideoDecoderControlState.PREPARED
        return generation
    }

    fun play() {
        ensureNotReleased()
        require(
            state == FfmpegVideoDecoderControlState.PREPARED ||
                state == FfmpegVideoDecoderControlState.PAUSED
        ) { "play requires PREPARED or PAUSED state" }

        commandSink.send(FfmpegVideoDecoderCommand.Play)
        state = FfmpegVideoDecoderControlState.PLAYING
    }

    fun pause() {
        ensureNotReleased()
        require(state == FfmpegVideoDecoderControlState.PLAYING) {
            "pause requires PLAYING state"
        }

        commandSink.send(FfmpegVideoDecoderCommand.Pause)
        state = FfmpegVideoDecoderControlState.PAUSED
    }

    fun seekTo(positionMs: Long): FfmpegVideoDecodeGeneration {
        ensureNotReleased()
        require(
            state == FfmpegVideoDecoderControlState.PREPARED ||
                state == FfmpegVideoDecoderControlState.PLAYING ||
                state == FfmpegVideoDecoderControlState.PAUSED
        ) { "seek requires an active prepared decoder" }

        val targetMs = positionMs.coerceAtLeast(0L)
        val generation = gate.advance()

        commandSink.send(
            FfmpegVideoDecoderCommand.Seek(
                generation = generation,
                positionMs = targetMs,
            ),
        )
        return generation
    }

    fun stop() {
        ensureNotReleased()
        if (state == FfmpegVideoDecoderControlState.STOPPED) return

        commandSink.send(FfmpegVideoDecoderCommand.Stop)
        state = FfmpegVideoDecoderControlState.STOPPED
    }

    fun release() {
        if (state == FfmpegVideoDecoderControlState.RELEASED) return

        commandSink.send(FfmpegVideoDecoderCommand.Release)
        state = FfmpegVideoDecoderControlState.RELEASED
    }

    private fun ensureNotReleased() {
        check(state != FfmpegVideoDecoderControlState.RELEASED) {
            "decoder controller is released"
        }
    }
}
