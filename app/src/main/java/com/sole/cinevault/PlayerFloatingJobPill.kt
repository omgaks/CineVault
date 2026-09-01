package com.sole.cinevault

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright

/**
 * Reusable CineVault pattern for long-running automatic work.
 *
 * Short tap = reopen the related full window.
 * Long-press + drag = move the pill using the existing proven popup wrapper.
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

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 14.dp)
    ) {
        DraggableFloatingPopup(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            popupWidth = 190.dp,
            popupMaxHeight = 56.dp,
            onUserInteraction = {},
        ) {
            FloatingJobPill(
                label = label,
                progress = progress,
                onClick = onOpen,
            )
        }
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
