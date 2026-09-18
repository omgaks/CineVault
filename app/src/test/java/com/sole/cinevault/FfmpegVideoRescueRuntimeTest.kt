package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueRuntimeTest {

    private sealed interface NativeCall {
        data object Create : NativeCall
        data class Prepare(
            val generation: FfmpegVideoDecodeGeneration,
            val positionMs: Long,
        ) : NativeCall
        data object Play : NativeCall
        data object Release : NativeCall
    }

    private class FakeBridge : FfmpegVideoNativeBridge {
        override var isCreated = false
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

        override fun play() {
            calls += NativeCall.Play
        }

        override fun pause() = Unit

        override fun seek(
            generation: FfmpegVideoDecodeGeneration,
            positionMs: Long,
        ) = Unit

        override fun stop() = Unit

        override fun release() {
            calls += NativeCall.Release
            isCreated = false
        }

        fun emit(event: FfmpegVideoDecodeEvent) {
            requireNotNull(listener).onEvent(event)
        }
    }

    @Test
    fun runtimeWiresControllerThroughBridge() {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { },
        )

        runtime.createNativeBridge()
        val generation = runtime.decoderController.prepare(2_000L)
        runtime.decoderController.play()

        assertEquals(
            listOf(
                NativeCall.Create,
                NativeCall.Prepare(generation, 2_000L),
                NativeCall.Play,
            ),
            bridge.calls,
        )
    }

    @Test
    fun nativeFrameFlowsThroughRuntimeToSink() {
        val bridge = FakeBridge()
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { received += it },
        )

        runtime.createNativeBridge()
        val generation = runtime.decoderController.prepare(1_000L)
        runtime.clock.start(1_000L)

        bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation = generation,
                frame = FfmpegDecodedVideoFrame(
                    presentationTimeMs = 1_005L,
                    widthPx = 1920,
                    heightPx = 1080,
                ),
            ),
        )

        assertEquals(1, runtime.framePump.queuedFrameCount)
        runtime.framePump.presentDueFrame()

        assertEquals(1, received.size)
        assertEquals(1_005L, runtime.clock.positionMs)
        assertEquals(
            FfmpegVideoOutputTarget(1920, 1080),
            runtime.output.target,
        )
    }

    @Test
    fun staleNativeFrameIsRejectedAfterGenerationAdvance() {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { },
        )

        runtime.createNativeBridge()
        val stale = runtime.decoderController.prepare()
        runtime.decoderController.seekTo(5_000L)

        bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation = stale,
                frame = FfmpegDecodedVideoFrame(
                    1_000L,
                    1920,
                    1080,
                ),
            ),
        )

        assertEquals(0, runtime.framePump.queuedFrameCount)
    }

    @Test
    fun releaseFlushesQueueDetachesOutputStopsClockAndReleasesBridge() {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { },
        )

        runtime.createNativeBridge()
        val generation = runtime.decoderController.prepare()
        runtime.clock.start(1_000L)

        bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation,
                FfmpegDecodedVideoFrame(1_005L, 1920, 1080),
            ),
        )
        runtime.framePump.presentDueFrame()
        bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation,
                FfmpegDecodedVideoFrame(1_010L, 1920, 1080),
            ),
        )

        runtime.release()

        assertTrue(runtime.isReleased)
        assertEquals(0, runtime.framePump.queuedFrameCount)
        assertEquals(null, runtime.output.target)
        assertFalse(runtime.clock.isRunning)
        assertEquals(0L, runtime.clock.positionMs)
        assertFalse(bridge.isCreated)
        assertEquals(NativeCall.Release, bridge.calls.last())
    }

    @Test
    fun releaseIsIdempotent() {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { },
        )
        runtime.createNativeBridge()

        runtime.release()
        runtime.release()

        assertEquals(
            1,
            bridge.calls.count { it == NativeCall.Release },
        )
    }

    @Test(expected = IllegalStateException::class)
    fun createBridgeAfterRuntimeReleaseIsRejected() {
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = FakeBridge(),
            frameSink = FfmpegDecodedVideoFrameSink { },
        )

        runtime.release()
        runtime.createNativeBridge()
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidQueueCapacityIsRejected() {
        FfmpegVideoRescueRuntime.create(
            nativeBridge = FakeBridge(),
            frameSink = FfmpegDecodedVideoFrameSink { },
            frameQueueCapacity = 0,
        )
    }

    @Test
    fun defaultFactoryCreatesUsableRuntime() {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntimeFactory.DEFAULT.create(
            bridge,
            FfmpegDecodedVideoFrameSink { },
        )

        runtime.createNativeBridge()

        assertTrue(bridge.isCreated)
        assertFalse(runtime.isReleased)
    }
}
