package com.sole.cinevault

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val StatusPillAmber = Color(0xFFFFB547)
private val StatusPillText = Color(0xFFF5F5F7)

@Composable
fun PlaybackStatusPill(
    snapshot: PlaybackDiagnosticsSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(snapshot) {
        buildPlaybackStatusPillPresentation(snapshot)
    }
    val labels = presentation.rotatingLabels
    var labelIndex by remember(labels) { mutableIntStateOf(0) }

    LaunchedEffect(labels) {
        labelIndex = 0
        if (labels.size <= 1) return@LaunchedEffect

        while (true) {
            delay(2800L)
            labelIndex = (labelIndex + 1) % labels.size
        }
    }

    val shape = RoundedCornerShape(999.dp)
    val accent = if (presentation.emphasized) {
        StatusPillAmber
    } else {
        StatusPillAmber.copy(alpha = 0.82f)
    }

    Box(
        modifier = modifier
            .widthIn(min = 74.dp, max = 188.dp)
            .background(
                color = Color.Black.copy(
                    alpha = if (presentation.emphasized) 0.72f else 0.58f,
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = accent.copy(
                    alpha = if (presentation.emphasized) 0.95f else 0.58f,
                ),
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = labels[labelIndex],
            animationSpec = tween(durationMillis = 280),
            label = "playbackStatusPillText",
        ) { label ->
            Text(
                text = label,
                color = if (presentation.emphasized) {
                    StatusPillAmber
                } else {
                    StatusPillText
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
