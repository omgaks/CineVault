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
 * Long-running job pill.
 *
 * The pill owns normalized coordinates rather than raw pixels. This keeps it
 * on-screen when the player rotates or changes window size. The default safe
 * home is top-right, away from the centre/right subtitle panels seen during
 * Tracks/Studio use. Long-press + drag still lets the user move it.
 */
@Composable
internal fun BoxScope.PlayerFloatingJobOverlay(
    visible: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    label: String,
    progress: Int?,
    onOpen: () -> Unit,
) {
    if (!visible) return

    val density = LocalDensity.current
    val popupWidth = 190.dp
    val popupHeight = 56.dp
    val edgePadding = 14.dp

    var bias by remember { mutableStateOf(DefaultFloatingJobBias) }

    val maxX = with(density) {
        ((containerWidth - popupWidth) / 2 - edgePadding).coerceAtLeast(0.dp).toPx()
    }
    val maxY = with(density) {
        ((containerHeight - popupHeight) / 2 - edgePadding).coerceAtLeast(0.dp).toPx()
    }

    // Re-clamp normalized state whenever dimensions change. Because position is
    // normalized, rotation expands/contracts the travel area without stranding
    // the pill at stale portrait pixel coordinates.
    LaunchedEffect(containerWidth, containerHeight) {
        bias = clampFloatingJobBias(bias.x, bias.y)
    }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset {
                IntOffset(
                    x = (bias.x * maxX).roundToInt(),
                    y = (bias.y * maxY).roundToInt(),
                )
            }
            .pointerInput(maxX, maxY) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    change.consume()
                    val nextX =
                        if (maxX > 0f) bias.x + (dragAmount.x / maxX) else bias.x
                    val nextY =
                        if (maxY > 0f) bias.y + (dragAmount.y / maxY) else bias.y
                    bias = clampFloatingJobBias(nextX, nextY)
                }
            }
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
            .clip(RoundedCornerShape(50))
            .background(SpaceDeep.copy(alpha = 0.86f))
            .border(
                width = 1.dp,
                color = AmberGlow.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
            )
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
