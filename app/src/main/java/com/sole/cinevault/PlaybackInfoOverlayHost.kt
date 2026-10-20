package com.sole.cinevault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class PlaybackInfoSurface {
    NONE,
    INFO,
    REPORT,
    MATRIX,
}

@Composable
internal fun BoxScope.PlaybackInfoOverlayHost(
    snapshot: PlaybackDiagnosticsSnapshot,
    compatibilityRecorder: PlaybackCompatibilitySessionRecorder,
    infoRequested: Boolean,
    onInfoRequestedChange: (Boolean) -> Unit,
    topPadding: Dp,
    sidePadding: Dp,
    railBottomInset: Dp = 120.dp,
    railMaxHeight: Dp = 400.dp,
) {
    var surface by remember { mutableStateOf(PlaybackInfoSurface.NONE) }

    val panelRequested = surface != PlaybackInfoSurface.NONE
    val visibility = playbackInfoVisibility(
        panelRequested = panelRequested,
        fallbackOccurred = snapshot.fallbackOccurred,
    )

    fun closeInfo() {
        surface = PlaybackInfoSurface.NONE
        onInfoRequestedChange(false)
    }

    // The HW pill (in the left pill column) opens and closes the panel.
    LaunchedEffect(infoRequested) {
        surface = if (infoRequested) {
            if (surface == PlaybackInfoSurface.NONE) PlaybackInfoSurface.INFO else surface
        } else {
            PlaybackInfoSurface.NONE
        }
    }

    BackHandler(enabled = visibility.showPanel) {
        surface = when (surface) {
            PlaybackInfoSurface.MATRIX -> PlaybackInfoSurface.REPORT
            PlaybackInfoSurface.REPORT -> PlaybackInfoSurface.INFO
            PlaybackInfoSurface.INFO,
            PlaybackInfoSurface.NONE -> {
                onInfoRequestedChange(false)
                PlaybackInfoSurface.NONE
            }
        }
    }

    if (visibility.showPanel) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    closeInfo()
                },
        )
    }

    AnimatedVisibility(
        visible = surface == PlaybackInfoSurface.INFO,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = lumenRailEndInset(), bottom = railBottomInset)
            .heightIn(max = railMaxHeight),
    ) {
        PlaybackInfoPanel(
            snapshot = snapshot,
            onDismiss = { closeInfo() },
            onShowCompatibilityReport = {
                surface = PlaybackInfoSurface.REPORT
            },
        )
    }

    AnimatedVisibility(
        visible = surface == PlaybackInfoSurface.REPORT,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = lumenRailEndInset(), bottom = railBottomInset)
            .heightIn(max = railMaxHeight),
    ) {
        val entries = compatibilityRecorder.entries()

        PlaybackCompatibilityReportPanel(
            entries = entries,
            report = compatibilityRecorder.report(),
            onBack = { surface = PlaybackInfoSurface.INFO },
            onDismiss = { closeInfo() },
            onShowMatrix = {
                surface = PlaybackInfoSurface.MATRIX
            },
        )
    }

    AnimatedVisibility(
        visible = surface == PlaybackInfoSurface.MATRIX,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = lumenRailEndInset(), bottom = railBottomInset)
            .heightIn(max = railMaxHeight),
    ) {
        PlaybackCompatibilityMatrixPanel(
            entries = compatibilityRecorder.entries(),
            onBack = { surface = PlaybackInfoSurface.REPORT },
            onDismiss = { closeInfo() },
        )
    }
}
