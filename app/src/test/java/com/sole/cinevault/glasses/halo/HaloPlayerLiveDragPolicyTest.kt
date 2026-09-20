package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerLiveDragPolicyTest {

    @Test
    fun brightnessRoutesToPlayerAction() {
        assertEquals(
            HaloPlayerLiveDragRoute.PLAYER_ACTION,
            HaloPlayerLiveDragPolicy.route(
                HaloPlayerGestureIntent.Brightness(0.1f)
            ),
        )
    }

    @Test
    fun volumeRoutesToPlayerAction() {
        assertEquals(
            HaloPlayerLiveDragRoute.PLAYER_ACTION,
            HaloPlayerLiveDragPolicy.route(
                HaloPlayerGestureIntent.Volume(0.1f)
            ),
        )
    }

    @Test
    fun seekRoutesToPlayerAction() {
        assertEquals(
            HaloPlayerLiveDragRoute.PLAYER_ACTION,
            HaloPlayerLiveDragPolicy.route(
                HaloPlayerGestureIntent.Seek(0.1f)
            ),
        )
    }

    @Test
    fun canonicalUiRoutesToExistingHaloPointer() {
        assertEquals(
            HaloPlayerLiveDragRoute.CANONICAL_POINTER,
            HaloPlayerLiveDragPolicy.route(
                HaloPlayerGestureIntent.CanonicalUi
            ),
        )
    }

    @Test
    fun absentIntentRoutesNowhere() {
        assertEquals(
            HaloPlayerLiveDragRoute.NONE,
            HaloPlayerLiveDragPolicy.route(null),
        )
    }

    @Test
    fun canonicalPointerIsNowOwnedByLiveDragSurface() {
        assertTrue(
            HaloPlayerLiveDragPolicy.shouldConsume(
                HaloPlayerGestureIntent.CanonicalUi
            )
        )
        assertFalse(HaloPlayerLiveDragPolicy.shouldConsume(null))
    }
}
