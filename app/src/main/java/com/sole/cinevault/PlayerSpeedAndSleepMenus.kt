package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerSpeedAndSleepMenus(
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    topClusterPaddingTop: Dp,
    clusterHeightPx: Float,
    isLandscape: Boolean,
    sidePadding: Dp,
    smallMenuWidth: Dp,
    smallMenuMaxHeight: Dp,
    onSpeedSelected: (Float) -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSleepSelected: (Int) -> Unit,
    onDismissSleepMenu: () -> Unit,
) {
    val density = LocalDensity.current
    val clusterHeightDp = with(density) { clusterHeightPx.toDp() }
    val titleRowOffset = if (isLandscape) 0.dp else 46.dp

    SpeedAndSleepMenuPopups(
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        playbackSpeed = playbackSpeed,
        sleepTimerMinutes = sleepTimerMinutes,
        topClusterPaddingTop = topClusterPaddingTop,
        titleRowOffset = titleRowOffset,
        clusterHeightDp = clusterHeightDp,
        sidePadding = sidePadding,
        smallMenuWidth = smallMenuWidth,
        smallMenuMaxHeight = smallMenuMaxHeight,
        onSpeedSelected = onSpeedSelected,
        onDismissSpeedMenu = onDismissSpeedMenu,
        onSleepSelected = onSleepSelected,
        onDismissSleepMenu = onDismissSleepMenu,
    )
}
