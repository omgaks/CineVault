package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoDecoderControllerTest {

    private data class Fixture(
        val gate: FfmpegVideoDecodeGenerationGate,
        val commands: MutableList<FfmpegVideoDecoderCommand>,
        val controller: FfmpegVideoDecoderController,
    )

    private fun fixture(): Fixture {
        val gate = FfmpegVideoDecodeGenerationGate()
        val commands = mutableListOf<FfmpegVideoDecoderCommand>()
        return Fixture(
            gate = gate,
            commands = commands,
            controller = FfmpegVideoDecoderController(
                gate = gate,
                commandSink = FfmpegVideoDecoderCommandSink {
                    commands += it
                },
            ),
        )
    }

    @Test
    fun prepareAdvancesGenerationAndSendsPrepare() {
        val f = fixture()

        val generation = f.controller.prepare(1_500L)

        assertEquals(FfmpegVideoDecodeGeneration(1L), generation)
        assertEquals(generation, f.gate.activeGeneration)
        assertEquals(
            FfmpegVideoDecoderCommand.Prepare(generation, 1_500L),
            f.commands.single(),
        )
        assertEquals(
            FfmpegVideoDecoderControlState.PREPARED,
            f.controller.state,
        )
    }

    @Test
    fun negativePreparePositionClampsToZero() {
        val f = fixture()

        val generation = f.controller.prepare(-100L)

        assertEquals(
            FfmpegVideoDecoderCommand.Prepare(generation, 0L),
            f.commands.single(),
        )
    }

    @Test
    fun playPausePlayProducesDeterministicCommands() {
        val f = fixture()
        f.controller.prepare()
        f.commands.clear()

        f.controller.play()
        f.controller.pause()
        f.controller.play()

        assertEquals(
            listOf(
                FfmpegVideoDecoderCommand.Play,
                FfmpegVideoDecoderCommand.Pause,
                FfmpegVideoDecoderCommand.Play,
            ),
            f.commands,
        )
        assertEquals(
            FfmpegVideoDecoderControlState.PLAYING,
            f.controller.state,
        )
    }

    @Test
    fun seekAdvancesGenerationAndSendsTarget() {
        val f = fixture()
        f.controller.prepare()
        f.controller.play()
        f.commands.clear()
        val beforeSeek = f.gate.activeGeneration

        val generation = f.controller.seekTo(42_000L)

        assertTrue(generation.value > beforeSeek.value)
        assertEquals(generation, f.gate.activeGeneration)
        assertEquals(
            FfmpegVideoDecoderCommand.Seek(generation, 42_000L),
            f.commands.single(),
        )
        assertEquals(
            FfmpegVideoDecoderControlState.PLAYING,
            f.controller.state,
        )
    }

    @Test
    fun negativeSeekClampsToZero() {
        val f = fixture()
        f.controller.prepare()
        f.commands.clear()

        val generation = f.controller.seekTo(-1L)

        assertEquals(
            FfmpegVideoDecoderCommand.Seek(generation, 0L),
            f.commands.single(),
        )
    }

    @Test
    fun stopIsIdempotent() {
        val f = fixture()
        f.controller.prepare()
        f.commands.clear()

        f.controller.stop()
        f.controller.stop()

        assertEquals(
            listOf(FfmpegVideoDecoderCommand.Stop),
            f.commands,
        )
        assertEquals(
            FfmpegVideoDecoderControlState.STOPPED,
            f.controller.state,
        )
    }

    @Test
    fun releaseIsIdempotent() {
        val f = fixture()

        f.controller.release()
        f.controller.release()

        assertEquals(
            listOf(FfmpegVideoDecoderCommand.Release),
            f.commands,
        )
        assertEquals(
            FfmpegVideoDecoderControlState.RELEASED,
            f.controller.state,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun playBeforePrepareIsRejected() {
        fixture().controller.play()
    }

    @Test(expected = IllegalArgumentException::class)
    fun pauseBeforePlayIsRejected() {
        val f = fixture()
        f.controller.prepare()
        f.controller.pause()
    }

    @Test(expected = IllegalStateException::class)
    fun commandsAfterReleaseAreRejected() {
        val f = fixture()
        f.controller.release()
        f.controller.prepare()
    }
}
