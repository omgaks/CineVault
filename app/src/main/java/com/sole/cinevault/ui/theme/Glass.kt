package com.sole.cinevault.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
//  CineVault 2 — Glass Component Kit
//  The reusable building blocks of the space-glass look.
//  Every v2 screen is assembled from these.
// ============================================================

/**
 * Turns any composable into a frosted glass panel:
 * translucent fill + top sheen + gradient edge that catches light.
 */
fun Modifier.glassPanel(
    cornerRadius: Dp = 22.dp,
    fill: Color = GlassSurface
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(fill)
        .background(
            Brush.verticalGradient(
                0f to GlassHighlight,
                0.35f to Color.Transparent,
                1f to Color.Transparent
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GlassBorderTop, GlassBorderBottom)
            ),
            shape = shape
        )
}

/**
 * Draws a soft amber light behind an element — the signature glow.
 * Use behind play buttons, active icons, focused cards.
 */
fun Modifier.amberGlow(
    radius: Dp = 90.dp,
    alpha: Float = 0.35f
): Modifier = this.drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                AmberGlow.copy(alpha = alpha),
                AmberEmber.copy(alpha = alpha * 0.35f),
                Color.Transparent
            ),
            center = center,
            radius = radius.toPx()
        ),
        radius = radius.toPx(),
        center = center
    )
}

/** A ready-to-use frosted glass container. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    fill: Color = GlassSurface,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glassPanel(cornerRadius, fill),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Full-screen space backdrop with drifting amber ambience —
 * wrap any screen's content in this to give it the v2 atmosphere.
 * (The blur() effects require Android 12+; on older devices they
 * gracefully render as soft gradients.)
 */
@Composable
fun SpaceGlassBackground(
    modifier: Modifier = Modifier,
    glowAlpha: Float = 0.16f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SpaceDeep, SpaceBlack)))
    ) {
        // Warm ambience, top-left — like light spilling from a projector
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .offset(x = (-90).dp, y = (-70).dp)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AmberGlow.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        // Faint ember, bottom-right — balances the frame
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 90.dp)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AmberEmber.copy(alpha = glowAlpha * 0.9f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        content()
    }
}
