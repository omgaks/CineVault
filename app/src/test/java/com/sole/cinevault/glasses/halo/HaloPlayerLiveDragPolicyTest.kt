package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerLiveDragPolicyTest {

    @Test
    fun brightnessIsConsumed() {
        assertTrue(
            HaloPlayerLiveDragPolicy.shouldConsume(
                HaloPlayerGestureIntent.Brightness(0.1f)
            )
        )
    }

    @Test
    fun volumeIsConsumed() {
        assertTrue(
            HaloPlayerLiveDragPolicy.shouldConsume(
                HaloPlayerGestureIntent.Volume(0.1f)
            )
        )
    }

    @Test
    fun seekIsConsumed() {
        assertTrue(
            HaloPlayerLiveDragPolicy.shouldConsume(
                HaloPlayerGestureIntent.Seek(0.1f)
            )
        )
    }

    @Test
    fun canonicalUiIsNotConsumed() {
        assertFalse(
            HaloPlayerLiveDragPolicy.shouldConsume(
                HaloPlayerGestureIntent.CanonicalUi
            )
        )
    }

    @Test
    fun absentSessionIntentIsNotConsumed() {
        assertFalse(HaloPlayerLiveDragPolicy.shouldConsume(null))
    }
}
