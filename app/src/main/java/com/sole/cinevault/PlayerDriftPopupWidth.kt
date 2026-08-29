package com.sole.cinevault

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps the drift-correction popup at the same minimum width used by the
 * original inline player calculation.
 */
internal fun playerDriftPopupWidth(
    trackSelectorWidth: Dp,
): Dp = trackSelectorWidth.coerceAtLeast(220.dp)
