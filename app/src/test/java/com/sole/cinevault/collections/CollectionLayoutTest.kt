package com.sole.cinevault.collections

import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.responsive.CineWindowSizeInfo
import com.sole.cinevault.ui.responsive.WindowHeightClass
import com.sole.cinevault.ui.responsive.WindowWidthClass
import com.sole.cinevault.ui.responsive.classifyCineWindowHeight
import com.sole.cinevault.ui.responsive.classifyCineWindowWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLayoutTest {
    private fun info(w: Float, h: Float) = CineWindowSizeInfo(
        widthDp = w.dp, heightDp = h.dp,
        widthClass = classifyCineWindowWidth(w), heightClass = classifyCineWindowHeight(h),
        isPortrait = h >= w, isTabletClass = minOf(w, h) >= 600f, fontScale = 1f
    )

    @Test fun phonePortraitIsSinglePane() {
        val s = collectionLayoutFor(info(390f, 844f))
        assertFalse(s.twoPane)
        assertEquals(104.dp, s.minPosterWidth)
        assertEquals(300.dp, s.heroHeight)
    }

    @Test fun mediumWindowIsTwoPaneWithNarrowLeft() {
        val s = collectionLayoutFor(info(700f, 900f))
        assertTrue(s.twoPane)
        assertEquals(340.dp, s.leftPaneWidth)
        assertEquals(124.dp, s.minPosterWidth)
    }

    @Test fun expandedTabletIsTwoPaneWithWideLeft() {
        val s = collectionLayoutFor(info(1280f, 800f))
        assertTrue(s.twoPane)
        assertEquals(440.dp.value.coerceAtMost(1280f * 0.36f), s.leftPaneWidth.value, 0.01f)
        assertEquals(150.dp, s.minPosterWidth)
    }

    @Test fun landscapePhoneDropsHeroInsteadOfClipping() {
        // 900 x 400: wide enough for two panes, too short for a tall hero.
        val s = collectionLayoutFor(info(900f, 400f))
        assertTrue(s.twoPane)
        assertTrue(s.shortHero)
        assertEquals(0.dp, s.heroHeight)
        assertEquals(WindowHeightClass.COMPACT, classifyCineWindowHeight(400f))
    }

    @Test fun splitScreenNarrowWindowFallsBackToSinglePane() {
        assertEquals(WindowWidthClass.COMPACT, classifyCineWindowWidth(500f))
        assertFalse(collectionLayoutFor(info(500f, 900f)).twoPane)
    }

    @Test fun leftPaneNeverExceedsThirtySixPercentOnExpanded() {
        val s = collectionLayoutFor(info(900f, 800f))
        assertTrue(s.leftPaneWidth.value <= 900f * 0.36f + 0.01f)
    }
}
