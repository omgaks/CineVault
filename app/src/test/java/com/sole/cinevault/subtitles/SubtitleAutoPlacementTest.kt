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
    fun portraitWideVideo_staysInsidePicture() {
        // 1080x2000 screen, 16:9 video -> picture 607.5px tall, bottom bar ~696px.
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1080f, 2000f, 16f / 9f, 40f)!!
        val pictureHeight = 1080f / (16f / 9f)
        val bar = (2000f - pictureHeight) / 2f
        val paddingPx = f * 2000f
        assertEquals(bar + pictureHeight * 0.075f, paddingPx, 1.5f)
        assertTrue("must stay well above the device bottom", f > 0.30f)
    }

    @Test
    fun fullScreenVideo_usesPictureSafeMargin() {
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1600f, 900f, 16f / 9f, 40f)!!
        assertEquals(0.075f, f, 0.001f)
    }

    @Test
    fun thinBar_stillStaysInsidePicture() {
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1600f, 900f, 2.0f, 60f)!!
        val pictureHeight = 1600f / 2.0f
        val bar = (900f - pictureHeight) / 2f
        assertEquals((bar + pictureHeight * 0.075f) / 900f, f, 0.001f)
    }

    @Test
    fun zoomMode_usesVisibleViewportSafeMargin() {
        val f = SubtitleAutoPlacement.bottomPaddingFraction(1080f, 2000f, 16f / 9f, 40f, fitMode = false)!!
        assertEquals(0.075f, f, 0.001f)
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
    fun portraitWideFilm_staysInsidePicture() {
        // Portrait 1568x2350, picture y 751..1599.
        val f = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            subtitleViewBottomPx = 2350f, subtitleViewHeightPx = 2350f,
            pictureTopPx = 751f, pictureBottomPx = 1599f, textPx = 36f,
        )!!
        val paddingPx = f * 2350f
        assertEquals((2350f - 1599f) + (1599f - 751f) * 0.075f, paddingPx, 2f)
    }

    @Test
    fun landscapeThinBar_usesSamePictureRelativeRule() {
        val f = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            subtitleViewBottomPx = 1568f, subtitleViewHeightPx = 1568f,
            pictureTopPx = 148f, pictureBottomPx = 1420f, textPx = 46f,
        )!!
        val expected = (148f + (1420f - 148f) * 0.075f) / 1568f
        assertEquals(expected, f, 0.001f)
    }

    @Test
    fun lineCount_doesNotMoveBottomAnchor() {
        val one = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            2000f, 2000f, 600f, 1400f, 40f, lines = 1,
        )!!
        val three = SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            2000f, 2000f, 600f, 1400f, 40f, lines = 3,
        )!!
        assertEquals(one, three, 0.0001f)
    }

    @Test
    fun notLaidOut_returnsNull() {
        assertNull(SubtitleAutoPlacement.bottomPaddingFractionFromRects(0f, 0f, 0f, 100f, 40f))
        assertNull(SubtitleAutoPlacement.bottomPaddingFractionFromRects(2000f, 2000f, 100f, 100f, 40f))
    }
}
