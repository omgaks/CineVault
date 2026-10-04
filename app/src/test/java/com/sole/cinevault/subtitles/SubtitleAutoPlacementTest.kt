package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAutoPlacementTest {

    @Test
    fun unknownSizes_returnNull() {
        assertNull(SubtitleAutoPlacement.bottomPaddingFraction(0f, 800f, 1.78f, 40f))
        assertNull(SubtitleAutoPlacement.bottomPaddingFraction(1000f, 800f, 0f, 40f))
    }

    @Test
    fun portraitWideVideo_sitsJustUnderThePicture() {
        // 1080x2000 screen, 16:9 video -> picture 607px tall, bars of ~696px.
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1080f, 2000f, 16f / 9f, 40f)!!
        val bar = (2000f - 1080f / (16f / 9f)) / 2f
        val paddingPx = f * 2000f
        // Text block's top edge is ~half a line under the picture, not at the screen edge.
        val textTopFromPictureBottom = bar - paddingPx - 40f * 1.25f * 2
        assertEquals(20f, textTopFromPictureBottom, 1.5f)
        assertTrue("must not hug the bottom edge", f > 0.2f)
    }

    @Test
    fun fullScreenVideo_sitsInsideTheBottomOfThePicture() {
        // Aspect equal to the view: no bars, text goes just inside the picture.
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1600f, 900f, 16f / 9f, 40f)!!
        assertEquals(0.04f, f, 0.001f)
    }

    @Test
    fun thinBar_fallsBackToInsideThePicture() {
        // Slightly wider than the view: bar too thin for two lines.
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1600f, 900f, 2.0f, 60f)!!
        val bar = (900f - 1600f / 2.0f) / 2f
        assertEquals((bar + (1600f / 2.0f) * 0.04f) / 900f, f, 0.001f)
    }

    @Test
    fun zoomMode_ignoresBars() {
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1080f, 2000f, 16f / 9f, 40f, fitMode = false)!!
        assertEquals(0.04f, f, 0.001f)
        assertNotNull(f)
    }
}

class AutoSubtitleSizeTest {
    @Test
    fun tabletLandscapeAndPortraitMatchTargets() {
        assertEquals(18f, autoSubtitleSizeSp(18f, 1200f, 800f), 0.01f)
        assertEquals(16f, autoSubtitleSizeSp(16f, 800f, 1200f), 0.01f)
    }

    @Test
    fun phoneIsSmallerThanTablet() {
        val phoneLand = autoSubtitleSizeSp(16f, 850f, 392f)
        val phonePort = autoSubtitleSizeSp(14f, 392f, 850f)
        assertEquals(15f, phoneLand, 0.01f)
        assertEquals(13f, phonePort, 0.01f)
        assertTrue(phoneLand < 18f && phonePort < 16f)
    }

    @Test
    fun userPreferenceStillScalesEverywhere() {
        val normal = autoSubtitleSizeSp(18f, 1200f, 800f)
        val bigger = autoSubtitleSizeSp(22f, 1200f, 800f)
        assertTrue(bigger > normal * 1.15f)
    }
}


class AutoPlacementFromRectsTest {
    @Test
    fun portraitWideFilm_sitsJustUnderPicture() {
        // Portrait 1568x2350, 1.85:1 picture occupies y 751..1599 (the Ghost in the Shell case).
        val f = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            subtitleViewBottomPx = 2350f, subtitleViewHeightPx = 2350f,
            pictureTopPx = 751f, pictureBottomPx = 1599f, textPx = 36f,
        )!!
        val paddingPx = f * 2350f
        val textBlockTop = 2350f - paddingPx - 36f * 1.25f * 2
        assertEquals(1599f + 18f, textBlockTop, 2f) // half a line under the picture
    }

    @Test
    fun landscapeThinBar_staysInsideTheBottomOfThePicture() {
        val f = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            subtitleViewBottomPx = 1568f, subtitleViewHeightPx = 1568f,
            pictureTopPx = 148f, pictureBottomPx = 1420f, textPx = 46f,
        )!!
        val expected = (148f + (1420f - 148f) * 0.04f) / 1568f
        assertEquals(expected, f, 0.001f)
    }

    @Test
    fun notLaidOut_returnsNull() {
        assertNull(SubtitleAutoPlacement.bottomPaddingFractionFromRects(0f, 0f, 0f, 100f, 40f))
        assertNull(SubtitleAutoPlacement.bottomPaddingFractionFromRects(2000f, 2000f, 100f, 100f, 40f))
    }
}
