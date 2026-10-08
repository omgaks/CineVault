package com.sole.cinevault.collections

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.responsive.CineWindowSizeInfo
import com.sole.cinevault.ui.responsive.WindowHeightClass
import com.sole.cinevault.ui.responsive.WindowWidthClass

/**
 * How the collection page lays itself out for a given window. Pure function of
 * the project-wide [CineWindowSizeInfo], so it follows split-screen, foldables
 * and rotation live and is unit-testable without a device.
 */
data class CollectionLayoutSpec(
    /** Fixed left pane (hero, ring, next up) + scrolling poster grid. */
    val twoPane: Boolean,
    val leftPaneWidth: Dp,
    /** Poster grid uses GridCells.Adaptive(minPosterWidth), so columns follow width. */
    val minPosterWidth: Dp,
    /** Compact-height two-pane (landscape phone): drop the tall hero, let the pane scroll. */
    val shortHero: Boolean,
    val heroHeight: Dp
)

fun collectionLayoutFor(info: CineWindowSizeInfo): CollectionLayoutSpec {
    val twoPane = info.widthClass != WindowWidthClass.COMPACT
    val shortHero = info.heightClass == WindowHeightClass.COMPACT
    val leftPane = when (info.widthClass) {
        WindowWidthClass.COMPACT -> 0.dp
        WindowWidthClass.MEDIUM -> 340.dp
        WindowWidthClass.EXPANDED -> minOf(440.dp, info.widthDp * 0.36f)
    }
    val minPoster = when (info.widthClass) {
        WindowWidthClass.COMPACT -> 104.dp
        WindowWidthClass.MEDIUM -> 124.dp
        WindowWidthClass.EXPANDED -> 150.dp
    }
    val hero = when {
        shortHero -> 0.dp
        twoPane -> 220.dp
        else -> 300.dp
    }
    return CollectionLayoutSpec(twoPane, leftPane, minPoster, shortHero, hero)
}
