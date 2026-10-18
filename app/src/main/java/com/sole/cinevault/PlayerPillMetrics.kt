package com.sole.cinevault

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared sizes for the floating player pills so the lock pill, the right-hand
 * pill column and the mic pill always line up, in portrait and landscape.
 * Landscape screens are short, so the pills are a little more compact there.
 */
internal object PlayerPillMetrics {
    fun height(landscape: Boolean): Dp = if (landscape) 32.dp else 40.dp
    fun gap(landscape: Boolean): Dp = if (landscape) 4.dp else 8.dp
    fun iconBox(landscape: Boolean): Dp = if (landscape) 24.dp else 28.dp
    fun icon(landscape: Boolean): Dp = if (landscape) 20.dp else 24.dp
    fun top(landscape: Boolean): Dp = if (landscape) 10.dp else 14.dp
    val end: Dp = 14.dp
}
