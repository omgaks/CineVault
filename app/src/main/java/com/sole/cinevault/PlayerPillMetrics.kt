package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared sizes for the floating player pills so the lock pill, the right-hand
 * pill column and the voice button always line up, in portrait and landscape.
 * Landscape screens are short, so the pills are a little more compact there.
 */
internal object PlayerPillMetrics {
    fun height(landscape: Boolean): Dp = if (landscape) 32.dp else 40.dp
    fun gap(landscape: Boolean): Dp = if (landscape) 6.dp else 10.dp
    fun iconBox(landscape: Boolean): Dp = if (landscape) 24.dp else 28.dp
    fun icon(landscape: Boolean): Dp = if (landscape) 20.dp else 24.dp
    fun top(landscape: Boolean): Dp = if (landscape) 10.dp else 14.dp
    fun voiceSize(landscape: Boolean): Dp = if (landscape) 38.dp else 46.dp
    val end: Dp = 14.dp

    /** Width of the right-hand column, reported by the column so the lock pill can match it. */
    var rightColumnWidth: Dp by mutableStateOf(Dp.Unspecified)
}
