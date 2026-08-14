package com.sole.cinevault

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDisplayLayoutTest {

    @Test
    fun tabletPortraitUsesFullSizeLayout() {
        val layout = calculatePlayerDisplayLayout(800.dp, 1280.dp)

        assertFalse(layout.isLandscape)
        assertFalse(layout.isSmallPhone)
        assertFalse(layout.isCompactLandscape)
        assertEquals(1f, layout.scale, 0.001f)
        assertEquals(98.dp, layout.playButton)
        assertEquals(66.dp, layout.smallButton)
        assertEquals(152.dp, layout.bottomDockPadding)
        assertEquals(92.dp, layout.seekBottomPadding)
        assertEquals(18.dp, layout.topClusterPaddingTop)
    }

    @Test
    fun tabletLandscapeUsesLandscapeSpacing() {
        val layout = calculatePlayerDisplayLayout(1280.dp, 800.dp)

        assertTrue(layout.isLandscape)
        assertFalse(layout.isSmallPhone)
        assertFalse(layout.isCompactLandscape)
        assertEquals(0.90f, layout.scale, 0.001f)
        assertEquals(90.dp, layout.bottomDockPadding)
        assertEquals(17.dp, layout.seekBottomPadding)
        assertEquals(10.dp, layout.topClusterPaddingTop)
    }

    @Test
    fun shortLandscapeUsesCompactControlsAndPadding() {
        val layout = calculatePlayerDisplayLayout(900.dp, 400.dp)

        assertTrue(layout.isLandscape)
        assertTrue(layout.isSmallPhone)
        assertTrue(layout.isCompactLandscape)
        assertEquals(0.70f, layout.scale, 0.001f)
        assertEquals(8.dp, layout.sidePadding)
        assertEquals(76.dp, layout.bottomDockPadding)
        assertEquals(13.dp, layout.seekBottomPadding)
    }

    @Test
    fun narrowScreenShrinksDeckToFitButNeverBelowMinimum() {
        val narrow = calculatePlayerDisplayLayout(320.dp, 700.dp)
        val extremelyNarrow = calculatePlayerDisplayLayout(180.dp, 700.dp)

        assertTrue(narrow.scale < 0.78f)
        assertEquals(0.42f, extremelyNarrow.scale, 0.001f)
    }
}
