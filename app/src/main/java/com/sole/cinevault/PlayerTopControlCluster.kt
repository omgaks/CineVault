package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PlayerTopControlCluster.kt
 *
 * Owns the responsive top-of-player presentation, same in portrait and landscape:
 *  - top-left: name, ratings, then signal chips (4K, HDR, Atmos ...), wrapping when short of room;
 *  - under it, the left pill column (HW, autoplay, audio, sound FX, subs) given by [leftPills];
 *  - top-right: the floating pill column (lock, sleep, speed, connect, picture, PiP, mic).
 * State and actions remain owned by VideoPlayerScreen.
 */
@Composable
internal fun BoxScope.PlayerTopControlCluster(
    isLandscape: Boolean,
    topRowVisible: Boolean,
    topClusterPaddingTop: Dp,
    sidePadding: Dp,
    currentMeta: VideoWithMetadata?,
    title: String,
    fileName: String,
    diagnostics: PlaybackDiagnosticsSnapshot,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    onClusterHeightMeasured: (Float) -> Unit,
    leftPills: @Composable () -> Unit,
) {
    var rightWidthPx by remember { mutableStateOf(0) }
    val rightWidth = with(LocalDensity.current) { rightWidthPx.toDp() }

    TopIconCluster(
        isLandscape = isLandscape,
        playbackSpeed = playbackSpeed,
        sleepTimerActive = sleepTimerActive,
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        onSpeedClick = onSpeedClick,
        onSleepClick = onSleepClick,
        onPipClick = onPipClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = PlayerPillMetrics.top(isLandscape),
                end = PlayerPillMetrics.end,
            )
            .onGloballyPositioned {
                rightWidthPx = it.size.width
                onClusterHeightMeasured(it.size.height.toFloat())
            },
    )

    AnimatedVisibility(
        visible = topRowVisible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = sidePadding,
                top = topClusterPaddingTop,
                end = rightWidth + PlayerPillMetrics.end + 10.dp,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val chips = remember(diagnostics, fileName) { playerSpecChips(diagnostics, fileName) }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingTitlePill(
                    text = title,
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    modifier = Modifier.widthIn(max = if (isLandscape) 320.dp else 260.dp),
                )
                FloatingScoreCapsule(meta = currentMeta, vertical = false)
                chips.forEach { PlayerSpecChipView(it, compact = isLandscape) }
            }
            Spacer(Modifier.height(if (isLandscape) 8.dp else 12.dp))
            leftPills()
        }
    }
}
