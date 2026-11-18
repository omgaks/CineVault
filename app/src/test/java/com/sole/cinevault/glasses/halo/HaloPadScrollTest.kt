package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPadScrollTest {
    @Test fun scrollStartsAtThePointerAndNeverMovesIt() {
        val s = HaloPadScrollState()
        s.begin(HaloVector(0.4f, 0.8f))
        assertEquals(0.4f, s.last().x, 0.0001f)
        assertEquals(0.8f, s.last().y, 0.0001f)
    }

    @Test fun fingerUpMovesSyntheticFingerUpWithGain() {
        val s = HaloPadScrollState()
        s.begin(HaloVector(0.5f, 0.8f))
        val p = s.move(0f, -0.1f)
        assertEquals(0.8f - 0.1f * PAD_SCROLL_GAIN, p.y, 0.0001f)
        assertEquals(0.5f, p.x, 0.0001f)
    }

    @Test fun staysInsideTheScreen() {
        val s = HaloPadScrollState()
        s.begin(HaloVector(0.5f, 0.1f))
        val p = s.move(0f, -5f)
        assertEquals(0f, p.y, 0.0001f)
        assertTrue(s.move(5f, 5f).let { it.x <= 1f && it.y <= 1f })
    }

    @Test fun pointerOnlySampleNeverStartsADrag() {
        val c = HaloInteractionCoordinator()
        var drags = 0
        var t = 0L
        fun step(x: Float, pressed: Boolean) {
            t += 16
            drags += c.update(
                HaloPointerSample(x * 100, 50f, x, 0.5f, pressed, moveOnly = true), t, 16,
            ).dragEvents.size
        }
        step(0.1f, true); (1..20).forEach { step(0.1f + it * 0.03f, true) }; step(0.7f, false)
        assertEquals(0, drags)
    }
}
