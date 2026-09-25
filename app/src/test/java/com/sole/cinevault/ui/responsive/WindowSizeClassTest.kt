package com.sole.cinevault.ui.responsive

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizeClassTest {

    @Test
    fun widthBreakpointsMatchAdaptiveContract() {
        assertEquals(WindowWidthClass.COMPACT, classifyCineWindowWidth(320f))
        assertEquals(WindowWidthClass.COMPACT, classifyCineWindowWidth(599f))
        assertEquals(WindowWidthClass.MEDIUM, classifyCineWindowWidth(600f))
        assertEquals(WindowWidthClass.MEDIUM, classifyCineWindowWidth(839f))
        assertEquals(WindowWidthClass.EXPANDED, classifyCineWindowWidth(840f))
        assertEquals(WindowWidthClass.EXPANDED, classifyCineWindowWidth(1280f))
    }

    @Test
    fun heightBreakpointsMatchAdaptiveContract() {
        assertEquals(WindowHeightClass.COMPACT, classifyCineWindowHeight(360f))
        assertEquals(WindowHeightClass.COMPACT, classifyCineWindowHeight(479f))
        assertEquals(WindowHeightClass.MEDIUM, classifyCineWindowHeight(480f))
        assertEquals(WindowHeightClass.MEDIUM, classifyCineWindowHeight(899f))
        assertEquals(WindowHeightClass.EXPANDED, classifyCineWindowHeight(900f))
        assertEquals(WindowHeightClass.EXPANDED, classifyCineWindowHeight(1200f))
    }

    @Test
    fun splitScreenWidthCanReclassifyIndependentlyOfHeight() {
        assertEquals(WindowWidthClass.EXPANDED, classifyCineWindowWidth(1200f))
        assertEquals(WindowWidthClass.COMPACT, classifyCineWindowWidth(480f))
        assertEquals(WindowHeightClass.MEDIUM, classifyCineWindowHeight(800f))
    }

    @Test
    fun rotationUsesCurrentDimensionsRatherThanDeviceCategory() {
        assertEquals(WindowWidthClass.COMPACT, classifyCineWindowWidth(480f))
        assertEquals(WindowHeightClass.EXPANDED, classifyCineWindowHeight(900f))

        assertEquals(WindowWidthClass.EXPANDED, classifyCineWindowWidth(900f))
        assertEquals(WindowHeightClass.MEDIUM, classifyCineWindowHeight(480f))
    }
}
