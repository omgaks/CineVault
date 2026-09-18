package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun dualSubtitleDefaultGap_isZero() {
        assertEquals(0, DualSubtitleState().gapLines)
    }
}
