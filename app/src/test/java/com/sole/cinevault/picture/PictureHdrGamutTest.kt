package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureHdrGamutTest {
    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 0.00002)

    @Test fun blackStaysBlack() {
        val c = PictureHdrGamut.bt2020ToBt709(PictureHdrGamut.Rgb(0.0, 0.0, 0.0))
        near(0.0,c.r); near(0.0,c.g); near(0.0,c.b)
    }
    @Test fun whiteRemainsNeutral() {
        val c = PictureHdrGamut.bt2020ToBt709(PictureHdrGamut.Rgb(1.0, 1.0, 1.0))
        near(1.0,c.r); near(1.0,c.g); near(1.0,c.b)
    }
    @Test fun neutralGreyRemainsNeutral() {
        val c = PictureHdrGamut.bt709ToBt2020(PictureHdrGamut.Rgb(0.18, 0.18, 0.18))
        near(0.18,c.r); near(0.18,c.g); near(0.18,c.b)
    }
    @Test fun wideGamutRedIsNotSilentlyClipped() {
        val c = PictureHdrGamut.bt2020ToBt709(PictureHdrGamut.Rgb(1.0, 0.0, 0.0))
        assertTrue(c.r > 1.0); assertTrue(c.g < 0.0)
        assertFalse(PictureHdrGamut.isInUnitGamut(c))
    }
    @Test fun linearColourRoundTripIsStable() {
        val v = PictureHdrGamut.Rgb(0.31, 0.52, 0.14)
        val c = PictureHdrGamut.bt709ToBt2020(PictureHdrGamut.bt2020ToBt709(v))
        near(v.r,c.r); near(v.g,c.g); near(v.b,c.b)
    }
    @Test fun displayClampIsExplicit() {
        val c = PictureHdrGamut.clampForDisplay(PictureHdrGamut.Rgb(-0.3, 0.4, 1.8))
        near(0.0,c.r); near(0.4,c.g); near(1.0,c.b)
        assertTrue(PictureHdrGamut.isInUnitGamut(c))
    }
}
