package com.sole.cinevault

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.PlayerSpeedAndSleepMenus(
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    isLandscape: Boolean,
    railBottomInset: Dp,
    onSpeedSelected: (Float) -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSleepSelected: (Int) -> Unit,
    onDismissSleepMenu: () -> Unit,
) {
    SpeedAndSleepMenuPopups(
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        playbackSpeed = playbackSpeed,
        sleepTimerMinutes = sleepTimerMinutes,
        isLandscape = isLandscape,
        railBottomInset = railBottomInset,
        onSpeedSelected = onSpeedSelected,
        onDismissSpeedMenu = onDismissSpeedMenu,
        onSleepSelected = onSleepSelected,
        onDismissSleepMenu = onDismissSleepMenu,
    )
}
