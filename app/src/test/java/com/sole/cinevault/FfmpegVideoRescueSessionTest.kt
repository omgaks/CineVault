package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.*
import org.junit.Test

class FfmpegVideoRescueSessionTest {
    private sealed interface Call {
        data object Create: Call
        data class Prepare(val g:FfmpegVideoDecodeGeneration,val p:Long):Call
        data object Play:Call
        data object Pause:Call
        data class Seek(val g:FfmpegVideoDecodeGeneration,val p:Long):Call
        data object Stop:Call
        data object Release:Call
    }
    private class Bridge:FfmpegVideoNativeBridge {
        override var isCreated=false; private set
        val calls=mutableListOf<Call>(); private var listener:FfmpegVideoNativeEventListener?=null
        override fun create(eventListener:FfmpegVideoNativeEventListener){listener=eventListener;isCreated=true;calls+=Call.Create}
        override fun prepare(generation:FfmpegVideoDecodeGeneration,startPositionMs:Long){calls+=Call.Prepare(generation,startPositionMs)}
        override fun play(){calls+=Call.Play}
        override fun pause(){calls+=Call.Pause}
        override fun seek(generation:FfmpegVideoDecodeGeneration,positionMs:Long){calls+=Call.Seek(generation,positionMs)}
        override fun stop(){calls+=Call.Stop}
        override fun release(){calls+=Call.Release;isCreated=false}
        fun emit(e:FfmpegVideoDecodeEvent)=requireNotNull(listener).onEvent(e)
    }
    private data class Fx(val b:Bridge,val r:FfmpegVideoRescueRuntime,val s:FfmpegVideoRescueSession)
    private fun fx():Fx { val b=Bridge(); val r=FfmpegVideoRescueRuntime.create(b,FfmpegDecodedVideoFrameSink{}); return Fx(b,r,FfmpegVideoRescueSession(r)) }

    @Test fun prepareOwnsBridgeClockAndDecoder(){
        val f=fx(); val g=f.s.prepare(2000)
        assertEquals(FfmpegVideoDecodeGeneration(1),g); assertEquals(2000,f.r.clock.positionMs)
        assertTrue(f.r.clock.isRunning); assertEquals(FfmpegVideoRescueSessionState.READY,f.s.state)
        assertEquals(listOf(Call.Create,Call.Prepare(g,2000)),f.b.calls)
    }
    @Test fun playPauseKeepsLifecycleAligned(){
        val f=fx(); f.s.prepare(); f.b.calls.clear(); f.s.play(); assertTrue(f.r.clock.isRunning)
        f.s.pause(); assertFalse(f.r.clock.isRunning); assertEquals(listOf(Call.Play,Call.Pause),f.b.calls)
    }
    @Test fun seekFlushesMovesTimelineAndAdvancesGeneration(){
        val f=fx(); val old=f.s.prepare(1000)
        f.b.emit(FfmpegVideoDecodeEvent.Frame(old,FfmpegDecodedVideoFrame(1010,1920,1080)))
        val g=f.s.seekTo(42000)
        assertTrue(g.value>old.value); assertEquals(0,f.r.framePump.queuedFrameCount); assertEquals(42000,f.r.clock.positionMs)
        assertEquals(Call.Seek(g,42000),f.b.calls.last())
    }
    @Test fun staleFrameAfterSeekCannotReenterQueue(){
        val f=fx(); val stale=f.s.prepare(); val active=f.s.seekTo(5000)
        f.b.emit(FfmpegVideoDecodeEvent.Frame(stale,FfmpegDecodedVideoFrame(1000,1920,1080)))
        f.b.emit(FfmpegVideoDecodeEvent.Frame(active,FfmpegDecodedVideoFrame(5005,1920,1080)))
        assertEquals(1,f.r.framePump.queuedFrameCount)
    }
    @Test fun seekClearsTerminalFailure(){
        val f=fx(); val g=f.s.prepare()
        f.b.emit(FfmpegVideoDecodeEvent.Failure(g,"old failure")); assertEquals(FfmpegVideoDecodeTerminalState.FAILED,f.r.eventRouter.terminalState)
        f.s.seekTo(10000); assertEquals(FfmpegVideoDecodeTerminalState.NONE,f.r.eventRouter.terminalState); assertNull(f.r.eventRouter.failureMessage)
    }
    @Test fun stopFlushesAndIsIdempotent(){
        val f=fx(); val g=f.s.prepare(); f.b.emit(FfmpegVideoDecodeEvent.Frame(g,FfmpegDecodedVideoFrame(10,1920,1080)))
        f.s.stop(); f.s.stop(); assertEquals(0,f.r.framePump.queuedFrameCount); assertEquals(1,f.b.calls.count{it==Call.Stop})
    }
    @Test fun releaseIsIdempotentAndTerminal(){
        val f=fx(); f.s.prepare(); f.s.release(); f.s.release()
        assertTrue(f.r.isReleased); assertNull(f.s.generation); assertEquals(1,f.b.calls.count{it==Call.Release}); assertEquals(FfmpegVideoRescueSessionState.RELEASED,f.s.state)
    }
    @Test fun ownerReplacementReleasesPreviousRuntime(){
        val a=fx(); val b=fx(); val owner=FfmpegVideoRescueSessionOwner(); owner.install(a.r).prepare()
        val second=owner.install(b.r); assertTrue(a.r.isReleased); assertEquals(second,owner.session); assertFalse(b.r.isReleased)
    }
    @Test fun ownerClearReleasesAndDropsSession(){
        val f=fx(); val owner=FfmpegVideoRescueSessionOwner(); owner.install(f.r).prepare(); owner.clear()
        assertTrue(f.r.isReleased); assertNull(owner.session)
    }
    @Test(expected=IllegalStateException::class) fun playBeforePrepareRejected(){fx().s.play()}
    @Test(expected=IllegalStateException::class) fun secondPrepareRejected(){val f=fx();f.s.prepare();f.s.prepare()}
}
