package com.sole.cinevault.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing steps from the design kit (golden-ratio-ish rhythm). */
object CineSpace {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 13.dp
    val L: Dp = 21.dp
    val XL: Dp = 34.dp
    val XXL: Dp = 55.dp
}

@Composable
fun rememberCineWidthClass(): CineWidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return remember(width) { cineWidthClassFor(width) }
}
