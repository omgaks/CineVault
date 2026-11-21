package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.halo.HaloSurfaceBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplay3dTest {
    @Test
    fun appIsSplitOnlyWhenDisplay3dIsOnAndNoFullFrame3dFilm() {
        assertTrue(shouldSplitForEyes(display3dOn = true, fullFrame3dFilm = false))
        assertFalse(shouldSplitForEyes(display3dOn = true, fullFrame3dFilm = true))
        assertFalse(shouldSplitForEyes(display3dOn = false, fullFrame3dFilm = false))
        assertFalse(shouldSplitForEyes(display3dOn = false, fullFrame3dFilm = true))
    }

    @Test
    fun pointerWidthIsHalfWhileSplit() {
        HaloSurfaceBounds.widthFraction = 0.5f
        assertEquals(960, HaloSurfaceBounds.projectedWidthPx(1920))
        HaloSurfaceBounds.widthFraction = 1f
        assertEquals(1920, HaloSurfaceBounds.projectedWidthPx(1920))
    }
}
