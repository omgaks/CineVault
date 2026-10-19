package com.sole.cinevault

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
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
    isLandscape: Boolean,
    railBottomInset: Dp,
    onSpeedSelected: (Float) -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSleepSelected: (Int) -> Unit,
    onDismissSleepMenu: () -> Unit,
) {
    // Both now use the shared Lumen window on the right rail (see LumenWindow.kt).
    val speedAccent = androidx.compose.ui.graphics.Color(0xFFFFC94D)
    LumenWindow(
        visible = showSpeedMenu,
        accent = speedAccent,
        title = "Speed",
        icon = androidx.compose.material.icons.Icons.Rounded.Speed,
        size = LumenSize.S,
        isLandscape = isLandscape,
        bottomInset = railBottomInset,
    ) {
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f).forEach { speed ->
            LumenOptionRow(
                label = if (speed == 1.0f) "1x Normal" else if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x",
                selected = speed == playbackSpeed,
                accent = speedAccent,
                onClick = { onSpeedSelected(speed) },
            )
        }
    }

    val sleepAccent = androidx.compose.ui.graphics.Color(0xFFC792FF)
    LumenWindow(
        visible = showSleepMenu,
        accent = sleepAccent,
        title = "Sleep timer",
        icon = androidx.compose.material.icons.Icons.Rounded.Timer,
        size = LumenSize.S,
        isLandscape = isLandscape,
        bottomInset = railBottomInset,
    ) {
        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (mins, label) ->
            LumenOptionRow(
                label = label,
                selected = mins == sleepTimerMinutes,
                accent = sleepAccent,
                onClick = { onSleepSelected(mins) },
            )
        }
    }
}
