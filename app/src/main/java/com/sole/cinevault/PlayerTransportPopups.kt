package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * PlayerTransportPopups.kt
 *
 * First slice of the overlay-wiring extraction out of VideoPlayerScreen.kt.
 * Starting deliberately small (just these two popups) to prove the pattern
 * before extracting the other ~11 sheets/popups in the same block — see
 * conversation history for why: this block turned out to be more entangled
 * than a typical "pure move" (every popup shares layout locals computed
 * just above it in VideoPlayerScreen.kt), so validating the approach on
 * two simple, near-identical popups first is the safer order.
 *
 * Pure move, not a rewrite — same AnimatedVisibility wrapping, same
 * SpeedMenuPopup/SleepMenuPopup calls, same values, just relocated. All
 * shared layout math (topClusterPaddingTop, titleRowOffset, clusterHeightDp,
 * sidePadding, smallMenuWidth, smallMenuMaxHeight) is still computed in
 * VideoPlayerScreen.kt exactly as before and passed in as plain Dp values —
 * this file doesn't own or duplicate that math.
 *
 * Receiver is BoxScope (not a bare Modifier extension) because both popups
 * rely on Modifier.align(Alignment.TopEnd), which only resolves inside a
 * BoxScope — same constraint the original inline code had.
 */
@androidx.compose.runtime.Composable
fun BoxScope.SpeedAndSleepMenuPopups(
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    topClusterPaddingTop: Dp,
    titleRowOffset: Dp,
    clusterHeightDp: Dp,
    sidePadding: Dp,
    smallMenuWidth: Dp,
    smallMenuMaxHeight: Dp,
    onSpeedSelected: (Float) -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSleepSelected: (Int) -> Unit,
    onDismissSleepMenu: () -> Unit,
) {
    AnimatedVisibility(
        visible = showSpeedMenu,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = topClusterPaddingTop + titleRowOffset + clusterHeightDp + 8.dp, end = sidePadding)
    ) {
        SpeedMenuPopup(
            currentSpeed = playbackSpeed,
            popupWidth = smallMenuWidth,
            popupMaxHeight = smallMenuMaxHeight,
            onSpeedSelected = onSpeedSelected,
            onDismiss = onDismissSpeedMenu
        )
    }

    AnimatedVisibility(
        visible = showSleepMenu,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = topClusterPaddingTop + titleRowOffset + clusterHeightDp + 8.dp, end = sidePadding)
    ) {
        SleepMenuPopup(
            currentMinutes = sleepTimerMinutes,
            popupWidth = smallMenuWidth,
            popupMaxHeight = smallMenuMaxHeight,
            onSelected = onSleepSelected,
            onDismiss = onDismissSleepMenu
        )
    }
}
