package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PlayerTopControlCluster.kt
 *
 * Owns the responsive top-of-player presentation: ratings, now-playing title,
 * and PiP / sleep / speed controls. State and actions remain owned by
 * VideoPlayerScreen.
 */
@Composable
internal fun BoxScope.PlayerTopControlCluster(
    isLandscape: Boolean,
    topRowVisible: Boolean,
    topClusterPaddingTop: Dp,
    sidePadding: Dp,
    topIconSize: Dp,
    currentMeta: VideoWithMetadata?,
    title: String,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    onClusterHeightMeasured: (Float) -> Unit,
) {
    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = topClusterPaddingTop)
        ) {
            AnimatedVisibility(
                visible = topRowVisible,
                enter = fadeIn(animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(120)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = sidePadding)
            ) {
                FloatingScoreCapsule(meta = currentMeta, vertical = false)
            }

            AnimatedVisibility(
                visible = topRowVisible,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp)
            ) {
                NowPlayingTitlePill(text = title, fontSize = 13.sp)
            }

            TopIconCluster(
                isLandscape = true,
                iconSize = topIconSize,
                playbackSpeed = playbackSpeed,
                sleepTimerActive = sleepTimerActive,
                showSpeedMenu = showSpeedMenu,
                showSleepMenu = showSleepMenu,
                onSpeedClick = onSpeedClick,
                onSleepClick = onSleepClick,
                onPipClick = onPipClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = sidePadding + 62.dp)
                    .onGloballyPositioned { onClusterHeightMeasured(it.size.height.toFloat()) }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = topClusterPaddingTop)
        ) {
            AnimatedVisibility(
                visible = topRowVisible,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 72.dp)
            ) {
                NowPlayingTitlePill(text = title, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                if (topRowVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = sidePadding)
                    ) {
                        FloatingScoreCapsule(meta = currentMeta, vertical = true)
                    }
                }

                TopIconCluster(
                    isLandscape = false,
                    iconSize = topIconSize,
                    playbackSpeed = playbackSpeed,
                    sleepTimerActive = sleepTimerActive,
                    showSpeedMenu = showSpeedMenu,
                    showSleepMenu = showSleepMenu,
                    onSpeedClick = onSpeedClick,
                    onSleepClick = onSleepClick,
                    onPipClick = onPipClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = sidePadding)
                        .onGloballyPositioned { onClusterHeightMeasured(it.size.height.toFloat()) }
                )
            }
        }
    }
}
