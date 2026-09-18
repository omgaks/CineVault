package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoNativeBridgeAdapterTest {

    private sealed interface NativeCall {
        data object Create : NativeCall
        data class Prepare(
            val generation: FfmpegVideoDecodeGeneration,
            val positionMs: Long,
        ) : NativeCall
        data object Play : NativeCall
        data object Pause : NativeCall
        data class Seek(
            val generation: FfmpegVideoDecodeGeneration,
            val positionMs: Long,
        ) : NativeCall
        data object Stop : NativeCall
        data object Release : NativeCall
    }

    private class FakeBridge : FfmpegVideoNativeBridge {
        override var isCreated: Boolean = false
            private set

        val calls = mutableListOf<NativeCall>()
        private var listener: FfmpegVideoNativeEventListener? = null

        override fun create(eventListener: FfmpegVideoNativeEventListener) {
            listener = eventListener
            isCreated = true
            calls += NativeCall.Create
        }

        override fun prepare(
            generation: FfmpegVideoDecodeGeneration,
            startPositionMs: Long,
        ) {
            calls += NativeCall.Prepare(generation, startPositionMs)
        }

        override fun play() { calls += NativeCall.Play }
        override fun pause() { calls += NativeCall.Pause }

        override fun seek(
            generation: FfmpegVideoDecodeGeneration,
            positionMs: Long,
        ) {
            calls += NativeCall.Seek(generation, positionMs)
        }

        override fun stop() { calls += NativeCall.Stop }

        override fun release() {
            calls += NativeCall.Release
            isCreated = false
        }

        fun emit(event: FfmpegVideoDecodeEvent) {
            requireNotNull(listener).onEvent(event)
        }
    }

    private data class Fixture(
        val gate: FfmpegVideoDecodeGenerationGate,
        val pump: FfmpegVideoScheduledFramePump,
        val router: FfmpegVideoDecodeEventRouter,
        val bridge: FakeBridge,
        val adapter: FfmpegVideoNativeBridgeAdapter,
    )

    private fun fixture(): Fixture {
        val clock = FfmpegVideoPlaybackClockState().apply { start(1_000L) }
        val gate = FfmpegVideoDecodeGenerationGate()
        val pump = FfmpegVideoScheduledFramePump(
            queue = FfmpegVideoFrameQueue(),
            coordinator = FfmpegVideoFrameCoordinator(
                output = FfmpegVideoOutputBinding(),
                timeline = FfmpegVideoTimelineCoordinator(clock),
                sink = FfmpegDecodedVideoFrameSink { },
            ),
            clock = clock,
        )
        val router = FfmpegVideoDecodeEventRouter(
            gate = gate,
            frameIngress = FfmpegGenerationAwareVideoFrameIngress(
                gate = gate,
                framePump = pump,
            ),
        )
        val bridge = FakeBridge()
        return Fixture(
            gate,
            pump,
            router,
            bridge,
            FfmpegVideoNativeBridgeAdapter(bridge, router),
        )
    }

    @Test
    fun createInstallsNativeCallbackOnlyOnce() {
        val f = fixture()

        f.adapter.create()
        f.adapter.create()

        assertEquals(listOf(NativeCall.Create), f.bridge.calls)
        assertTrue(f.bridge.isCreated)
    }

    @Test
    fun decoderCommandsMapToNativeBridgeCalls() {
        val f = fixture()
        f.adapter.create()
        val generation = FfmpegVideoDecodeGeneration(2L)

        f.adapter.send(
            FfmpegVideoDecoderCommand.Prepare(generation, 5_000L),
        )
        f.adapter.send(FfmpegVideoDecoderCommand.Play)
        f.adapter.send(FfmpegVideoDecoderCommand.Pause)
        f.adapter.send(
            FfmpegVideoDecoderCommand.Seek(generation, 9_000L),
        )
        f.adapter.send(FfmpegVideoDecoderCommand.Stop)

        assertEquals(
            listOf(
                NativeCall.Create,
                NativeCall.Prepare(generation, 5_000L),
                NativeCall.Play,
                NativeCall.Pause,
                NativeCall.Seek(generation, 9_000L),
                NativeCall.Stop,
            ),
            f.bridge.calls,
        )
    }

    @Test
    fun nativeFrameCallbackRoutesIntoFramePump() {
        val f = fixture()
        f.adapter.create()

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation = f.gate.activeGeneration,
                frame = FfmpegDecodedVideoFrame(
                    1_005L,
                    1920,
                    1080,
                ),
            ),
        )

        assertEquals(1, f.pump.queuedFrameCount)
    }

    @Test
    fun nativeTerminalCallbackRoutesIntoEventRouter() {
        val f = fixture()
        f.adapter.create()

        f.bridge.emit(
            FfmpegVideoDecodeEvent.EndOfStream(
                f.gate.activeGeneration,
            ),
        )

        assertEquals(
            FfmpegVideoDecodeTerminalState.END_OF_STREAM,
            f.router.terminalState,
        )
    }

    @Test
    fun staleNativeCallbackIsRejectedByExistingGenerationGate() {
        val f = fixture()
        f.adapter.create()
        val stale = f.gate.activeGeneration
        f.gate.advance()

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                stale,
                FfmpegDecodedVideoFrame(1_005L, 1920, 1080),
            ),
        )

        assertEquals(0, f.pump.queuedFrameCount)
    }

    @Test
    fun releaseReleasesCreatedBridge() {
        val f = fixture()
        f.adapter.create()

        f.adapter.send(FfmpegVideoDecoderCommand.Release)

        assertFalse(f.bridge.isCreated)
        assertEquals(NativeCall.Release, f.bridge.calls.last())
    }

    @Test
    fun releaseWithoutCreateIsSafe() {
        val f = fixture()

        f.adapter.send(FfmpegVideoDecoderCommand.Release)

        assertFalse(f.bridge.isCreated)
        assertTrue(f.bridge.calls.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun commandBeforeCreateIsRejected() {
        fixture().adapter.send(FfmpegVideoDecoderCommand.Play)
    }

    @Test(expected = IllegalStateException::class)
    fun createAfterReleaseIsRejected() {
        val f = fixture()
        f.adapter.send(FfmpegVideoDecoderCommand.Release)
        f.adapter.create()
    }
}
