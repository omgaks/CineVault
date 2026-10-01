package com.sole.cinevault.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore

/**
 * Persistent CineVault Connect affordance.
 *
 * This component is deliberately stateless. The app root owns navigation and
 * CineVaultNearbyRuntime owns the connection. That keeps the pill alive while
 * Home / Library / Search / Settings destinations change and prevents it from
 * accidentally becoming a second connection state machine.
 */
@Composable
fun NetworkLibraryFloatingPill(
    connection: CineVaultRemoteConnection?,
    visible: Boolean,
    onOpenLibrary: (CineVaultRemoteConnection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connection != null
    val glow by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "networkLibraryPillGlow",
    )

    AnimatedVisibility(
        visible = visible && connected,
        modifier = modifier,
        enter = fadeIn(tween(220)) +
            slideInHorizontally(
                initialOffsetX = { it / 2 },
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ),
        exit = fadeOut(tween(150)) +
            slideOutHorizontally(
                targetOffsetX = { it / 3 },
                animationSpec = tween(180),
            ),
    ) {
        val active = connection ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(24.dp)

        Row(
            modifier = Modifier
                .shadow(14.dp, shape, ambientColor = AmberCore.copy(alpha = 0.18f * glow))
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xEE17181D),
                            Color(0xF21E1A16),
                            Color(0xEE17181D),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            AmberCore.copy(alpha = 0.72f),
                            Color.White.copy(alpha = 0.16f),
                            AmberCore.copy(alpha = 0.38f),
                        )
                    ),
                    shape = shape,
                )
                .clickable { onOpenLibrary(active) }
                .heightIn(min = 46.dp)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(19.dp),
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "NETWORK LIBRARY",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = active.device.displayName,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(86.dp),
            )

            Spacer(Modifier.width(7.dp))

            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = "Open network library",
                tint = AmberCore.copy(alpha = 0.95f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
