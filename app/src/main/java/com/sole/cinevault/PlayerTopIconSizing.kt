package com.sole.cinevault

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun calculatePlayerTopIconSize(
    uiScale: Float,
    playerScale: Float,
): Dp {
    return (44f * uiScale * playerScale.coerceAtLeast(0.75f)).dp
}
