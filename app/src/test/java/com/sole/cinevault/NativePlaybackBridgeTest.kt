package com.sole.cinevault

import org.junit.Assert.*
import org.junit.Test

class NativePlaybackBridgeTest {
    private class Fixture {
        val owner = Any()
        var state: NativePlaybackSnapshot? = NativePlaybackSnapshot(5000, 20000, false, false, 1f)
        var releases = 0
        val bridge = NativePlaybackBridge(owner, { state },
            { state = state?.copy(positionMs = if (state?.ended == true) 0 else state!!.positionMs, playing = true, ended = false) },
            { state = state?.copy(playing = false) },
            { target -> state = state?.copy(positionMs = target, ended = false) },
            { speed -> state = state?.copy(speed = speed) },
            { state = null }, { releases++; state = null })
    }
    @Test fun pauseAndPlayStayOnTheNativeOwner() {
        val f = Fixture()
        f.bridge.play()
        assertTrue(f.state!!.playing)
        f.bridge.pause()
        assertFalse(f.state!!.playing)
        assertSame(f.owner, f.bridge.owner)
    }
    @Test fun pipSeekClampsBothEndsOfTheFilm() {
        val f = Fixture()
        f.bridge.seekTo(-5000)
        assertEquals(0L, f.state!!.positionMs)
        f.bridge.seekTo(30000)
        assertEquals(20000L, f.state!!.positionMs)
    }
    @Test fun unknownDurationDoesNotTurnEveryForwardSeekIntoZero() {
        val f = Fixture()
        f.state = f.state!!.copy(durationMs = 0)
        f.bridge.seekTo(15000)
        assertEquals(15000L, f.state!!.positionMs)
    }
    @Test fun playAtEofUsesReplayAndClearsEndedState() {
        val f = Fixture()
        f.state = f.state!!.copy(positionMs = 20000, ended = true)
        f.bridge.play()
        assertEquals(0L, f.state!!.positionMs)
        assertTrue(f.state!!.playing)
        assertFalse(f.state!!.ended)
    }
    @Test fun releasedControlsCannotRestartAnOldScreen() {
        val f = Fixture()
        f.bridge.release()
        f.bridge.play()
        f.bridge.seekTo(9000)
        f.bridge.release()
        assertNull(f.bridge.snapshot)
        assertEquals(1, f.releases)
    }
    @Test fun sessionReceivesChangesAndExplicitSamePositionSeeks() {
        val f = Fixture()
        var changes = 0
        var seeks = 0
        val listener: (NativePlaybackSnapshot?, NativePlaybackSnapshot?, Boolean) -> Unit = { _, _, seek -> changes++; if (seek) seeks++ }
        f.bridge.addListener(listener)
        f.bridge.publish()
        f.bridge.publish()
        assertEquals(1, changes)
        f.bridge.seekTo(5000)
        assertEquals(1, seeks)
        f.bridge.removeListener(listener)
        f.bridge.play()
        assertEquals(2, changes)
    }
    @Test fun invalidSpeedDoesNotReachAudioTrack() {
        val f = Fixture()
        f.bridge.setSpeed(Float.NaN)
        assertEquals(1f, f.state!!.speed, 0f)
        f.bridge.setSpeed(4f)
        assertEquals(3f, f.state!!.speed, 0f)
    }
}
