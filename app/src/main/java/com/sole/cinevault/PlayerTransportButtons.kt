package com.sole.cinevault

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.*

@Composable
internal fun BackIconButton(size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to AmberGlow.copy(alpha = 0.10f), 1f to Color.Transparent))
        .border(1.2.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.55f), AmberDeep.copy(alpha = 0.25f))), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = AmberCore, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
internal fun GlassTransportButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = TextBright, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
internal fun FrostedPlayButton(isPlaying: Boolean, isEnded: Boolean, size: Dp, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "playGlow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "playGlowAlpha"
    )
    val density = LocalDensity.current
    val glowRadiusPx = with(density) { (size / 2f * 1.05f).toPx() }.coerceAtLeast(1f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
            .background(Brush.radialGradient(colors = listOf(AmberGlow.copy(alpha = glowAlpha * 0.55f), Color.Transparent), radius = glowRadiusPx))
            .border(
                width = 1.4.dp,
                brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep.copy(alpha = 0.30f))),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                isEnded -> Icons.Rounded.Replay
                isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = AmberCore,
            modifier = Modifier.size(size * 0.50f)
        )
    }
}

@Composable
internal fun IconCircle(icon: ImageVector, size: Dp, tint: Color = TextBright, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}
