package com.sole.cinevault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PlaybackInfoAmber = Color(0xFFFFB547)

@Composable
internal fun BoxScope.PlaybackInfoOverlayHost(
    snapshot: PlaybackDiagnosticsSnapshot,
    controlsVisible: Boolean,
    topPadding: Dp,
    sidePadding: Dp,
) {
    var panelRequested by remember { mutableStateOf(false) }

    val visibility = playbackInfoVisibility(
        panelRequested = panelRequested,
        fallbackOccurred = snapshot.fallbackOccurred,
    )

    BackHandler(enabled = visibility.showPanel) {
        panelRequested = false
    }

    AnimatedVisibility(
        visible = controlsVisible && !visibility.showPanel,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = topPadding + 42.dp,
                end = sidePadding,
            ),
    ) {
        PlaybackInfoTrigger(
            onClick = { panelRequested = true },
        )
    }

    AnimatedVisibility(
        visible = visibility.showPanel,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = topPadding + 42.dp,
                end = sidePadding,
            ),
    ) {
        PlaybackInfoPanel(
            snapshot = snapshot,
            onDismiss = { panelRequested = false },
        )
    }
}

@Composable
private fun PlaybackInfoTrigger(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                color = Color.Black.copy(alpha = 0.58f),
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = PlaybackInfoAmber.copy(alpha = 0.72f),
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            color = PlaybackInfoAmber,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
