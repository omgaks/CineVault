package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassesScreenSizePolicyTest {
    @Test fun stepsCycleThroughAllSizesAndWrap() {
        var v = 1f
        val seen = mutableListOf<String>()
        repeat(6) { v = GlassesScreenSizePolicy.next(v); seen += GlassesScreenSizePolicy.label(v) }
        assertEquals(listOf("95%", "90%", "85%", "80%", "75%", "100%"), seen)
    }

    @Test fun oddStoredValuesSnapToNearestStep() {
        assertEquals(0.9f, GlassesScreenSizePolicy.snap(0.91f), 0.0001f)
        assertEquals(0.75f, GlassesScreenSizePolicy.snap(0.2f), 0.0001f)
        assertEquals(1f, GlassesScreenSizePolicy.snap(7f), 0.0001f)
    }

    @Test fun threeDSideBySideAlwaysStaysFullSize() {
        assertEquals(1f, GlassesScreenSizePolicy.effective(0.8f, fullFrame3d = true), 0.0001f)
        assertEquals(0.8f, GlassesScreenSizePolicy.effective(0.8f, fullFrame3d = false), 0.0001f)
    }
}

class GlassesScreenSizeBothWaysTest {
    @Test fun smallerAndBiggerMoveOneStepAndStopAtTheEnds() {
        assertEquals(0.85f, GlassesScreenSizePolicy.smaller(0.9f), 0.0001f)
        assertEquals(0.95f, GlassesScreenSizePolicy.bigger(0.9f), 0.0001f)
        assertEquals(0.75f, GlassesScreenSizePolicy.smaller(0.75f), 0.0001f)
        assertEquals(1f, GlassesScreenSizePolicy.bigger(1f), 0.0001f)
    }

    @Test fun biggerFromAnySizeCanAlwaysReachFullWithoutGoingSmaller() {
        var v = 0.75f
        repeat(5) { v = GlassesScreenSizePolicy.bigger(v) }
        assertEquals(1f, v, 0.0001f)
    }
}
