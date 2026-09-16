package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePositionPolicyTest {

    @Test
    fun positionNeverDropsBelowVisibleFloor() {
        assertEquals(
            SubtitlePositionPolicy.MIN_BOTTOM_PADDING,
            SubtitlePositionPolicy.sanitize(-5f, 18f),
            0.0001f,
        )
    }

    @Test
    fun oldOffscreenPositionIsPulledBackIntoSafeArea() {
        val safe = SubtitlePositionPolicy.sanitize(0.90f, 18f)
        assertTrue(safe < 0.90f)
        assertEquals(
            SubtitlePositionPolicy.maxBottomPadding(18f),
            safe,
            0.0001f,
        )
    }

    @Test
    fun largerTextGetsMoreTopSafetyMargin() {
        assertTrue(
            SubtitlePositionPolicy.maxBottomPadding(32f) <
                SubtitlePositionPolicy.maxBottomPadding(12f)
        )
    }

    @Test
    fun ordinaryBottomPositionIsUnchanged() {
        assertEquals(
            0.10f,
            SubtitlePositionPolicy.sanitize(0.10f, 18f),
            0.0001f,
        )
    }
}
