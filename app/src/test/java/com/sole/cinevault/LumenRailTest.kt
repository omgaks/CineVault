package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumenRailTest {
    @Test fun widthScalesWithScreenAndStaysClamped() {
        assertEquals(240f, LumenRail.widthDp(2000f, LumenSize.S), 0.01f)
        assertEquals(200f, LumenRail.widthDp(600f, LumenSize.S), 0.01f)
        assertEquals(340f, LumenRail.widthDp(2000f, LumenSize.L), 0.01f)
        assertTrue(LumenRail.widthDp(320f, LumenSize.L) <= 320f * 0.6f)
    }

    @Test fun endInsetClearsRightPillColumn() {
        assertEquals(14f + 110f + 8f, LumenRail.endInsetDp(14f, 110f), 0.01f)
        assertEquals(14f + 120f + 8f, LumenRail.endInsetDp(14f, null), 0.01f)
    }

    @Test fun heightFitsBetweenTopAndBottomInsets() {
        assertEquals(300f, LumenRail.maxHeightDp(400f, 10f, 90f), 0.01f)
        assertEquals(120f, LumenRail.maxHeightDp(150f, 10f, 90f), 0.01f)
    }
}
