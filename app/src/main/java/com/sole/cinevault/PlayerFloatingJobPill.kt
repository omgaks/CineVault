package com.sole.cinevault

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright
import kotlin.math.roundToInt

/**
 * Long-running job pill. Floating status pills always stack under the last
 * right-hand pill (and under the auto-sync pill when that is showing).
 * [slot] is the position in the stack (0 = first). Tapping reopens the window.
 */
@Composable
internal fun BoxScope.PlayerFloatingJobOverlay(
    visible: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    label: String,
    progress: Int?,
    onOpen: () -> Unit,
    slot: Int = 0,
) {
    if (!visible) return

    val landscape = containerWidth > containerHeight
    val stackStep = 44.dp
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = PlayerPillMetrics.floatingTop(landscape) +
                    PlayerPillMetrics.autoSyncStackHeight + stackStep * slot,
                end = PlayerPillMetrics.end,
            )
    ) {
        FloatingJobPill(
            label = label,
            progress = progress,
            onClick = onOpen,
        )
    }
}

@Composable
private fun FloatingJobPill(
    label: String,
    progress: Int?,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "jobPillPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 850,
                easing = FastOutSlowInEasing,
            )
        ),
        label = "jobPillPulseAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 190.dp)
            .lumenFrame(LumenGold)
            .clip(LumenShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                strokeWidth = 2.dp,
                color = AmberCore,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = pulse },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (progress != null) "$label • ${progress.coerceIn(0, 100)}%" else label,
            color = TextBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
