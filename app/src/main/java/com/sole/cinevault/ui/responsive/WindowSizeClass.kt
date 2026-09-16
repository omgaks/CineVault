package com.sole.cinevault.ui.responsive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CineVault's project-wide window categories, based on available window size. */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }
enum class WindowHeightClass { COMPACT, MEDIUM, EXPANDED }

data class CineWindowSizeInfo(
    val widthDp: Dp,
    val heightDp: Dp,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val isPortrait: Boolean,
    val isTabletClass: Boolean,
    val fontScale: Float,
)

fun classifyCineWindowWidth(widthDp: Float): WindowWidthClass = when {
    widthDp < 600f -> WindowWidthClass.COMPACT
    widthDp < 840f -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

fun classifyCineWindowHeight(heightDp: Float): WindowHeightClass = when {
    heightDp < 480f -> WindowHeightClass.COMPACT
    heightDp < 900f -> WindowHeightClass.MEDIUM
    else -> WindowHeightClass.EXPANDED
}

/**
 * Reads the actual Compose window container, not the physical display.
 * It therefore follows split-screen/freeform resizing and configuration
 * changes naturally. Density supplies both dp conversion and system font scale.
 */
@Composable
@ReadOnlyComposable
fun rememberCineWindowSizeInfo(): CineWindowSizeInfo {
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { container.width.toDp() }
    val heightDp = with(density) { container.height.toDp() }
    val widthClass = classifyCineWindowWidth(widthDp.value)
    val heightClass = classifyCineWindowHeight(heightDp.value)

    return CineWindowSizeInfo(
        widthDp = widthDp,
        heightDp = heightDp,
        widthClass = widthClass,
        heightClass = heightClass,
        isPortrait = heightDp >= widthDp,
        isTabletClass = minOf(widthDp, heightDp) >= 600.dp,
        fontScale = density.fontScale,
    )
}

object CineResponsive {
    fun popupMaxWidth(info: CineWindowSizeInfo): Dp = when (info.widthClass) {
        WindowWidthClass.COMPACT -> minOf(info.widthDp * 0.92f, 360.dp)
        WindowWidthClass.MEDIUM -> 420.dp
        WindowWidthClass.EXPANDED -> 480.dp
    }

    fun popupMaxHeight(info: CineWindowSizeInfo): Dp = when {
        info.heightClass == WindowHeightClass.COMPACT -> info.heightDp * 0.82f
        info.widthClass == WindowWidthClass.COMPACT && info.isPortrait -> info.heightDp * 0.70f
        else -> info.heightDp * 0.82f
    }

    fun quickHudWidth(availableWidth: Dp, info: CineWindowSizeInfo): Dp {
        val cap = when (info.widthClass) {
            WindowWidthClass.COMPACT -> 264.dp
            WindowWidthClass.MEDIUM -> 300.dp
            WindowWidthClass.EXPANDED -> 320.dp
        }
        return minOf(availableWidth * 0.90f, cap)
    }

    fun screenHorizontalPadding(info: CineWindowSizeInfo): Dp = when (info.widthClass) {
        WindowWidthClass.COMPACT -> 16.dp
        WindowWidthClass.MEDIUM -> 32.dp
        WindowWidthClass.EXPANDED -> 48.dp
    }

    fun useTwoPaneLayout(info: CineWindowSizeInfo): Boolean =
        info.widthClass != WindowWidthClass.COMPACT
}
