package com.sole.cinevault

import com.sole.cinevault.ui.theme.CineWidthClass
import com.sole.cinevault.ui.theme.adaptiveColumnCount
import com.sole.cinevault.ui.theme.cineWidthClassFor
import com.sole.cinevault.ui.theme.usesSideRail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineLayoutMathTest {
    @Test fun widthClassesFollowTheWindow() {
        assertEquals(CineWidthClass.Compact, cineWidthClassFor(320))
        assertEquals(CineWidthClass.Compact, cineWidthClassFor(599))
        assertEquals(CineWidthClass.Medium, cineWidthClassFor(600))
        assertEquals(CineWidthClass.Medium, cineWidthClassFor(839))
        assertEquals(CineWidthClass.Expanded, cineWidthClassFor(840))
        assertEquals(CineWidthClass.Expanded, cineWidthClassFor(1920))
    }

    @Test fun columnsGrowWithWidth() {
        // 360dp phone with 16dp side padding = 328dp usable
        assertEquals(3, adaptiveColumnCount(328f))
        // 320dp phone
        assertEquals(2, adaptiveColumnCount(288f))
        // 800dp tablet window
        assertEquals(6, adaptiveColumnCount(768f))
        // 1280dp tablet
        assertEquals(10, adaptiveColumnCount(1600f))
    }

    @Test fun columnsNeverBelowMinimumOrAboveMaximum() {
        assertEquals(2, adaptiveColumnCount(0f))
        assertEquals(2, adaptiveColumnCount(-5f))
        assertEquals(2, adaptiveColumnCount(50f))
        assertEquals(4, adaptiveColumnCount(3000f, maxColumns = 4))
    }

    @Test fun sideRailForWideOrLandscapeWindows() {
        assertFalse(usesSideRail(390, 844))
        assertTrue(usesSideRail(844, 390))   // phone landscape
        assertTrue(usesSideRail(800, 1280))  // tablet portrait
        assertTrue(usesSideRail(1280, 800))
    }
}
