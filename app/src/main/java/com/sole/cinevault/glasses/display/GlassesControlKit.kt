package com.sole.cinevault.glasses.display

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.CineType
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.GlassBorderTop
import com.sole.cinevault.ui.theme.GlassHighlight
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright

// ============================================================
//  Glasses control kit.
//  The same space-glass look as the real player: frosted glass fill, a sheen across the
//  top, an edge that catches light, amber when lit. Used by the controls on the glasses and
//  by the tablet touchpad so both feel like one product.
// ============================================================

/** Frosted glass surface: fill, sheen and the light-catching edge. */
internal fun Modifier.glassSurface(shape: androidx.compose.ui.graphics.Shape, lit: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(if (lit) AmberCore.copy(alpha = 0.14f) else GlassSurfaceStrong)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
        .border(
            width = 1.dp,
            brush = if (lit) {
                Brush.verticalGradient(listOf(AmberCore.copy(alpha = 0.7f), AmberGlow.copy(alpha = 0.3f)))
            } else {
                Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
            },
            shape = shape,
        )

/** Round glass icon button (previous, next, -10s, +10s). */
@Composable
internal fun GlassIconButton(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp = 60.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .glassSurface(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = TextBright, modifier = Modifier.size(size * 0.46f))
    }
}

/** Glass pill with optional icon. Lit amber when [selected], like CineChip. */
@Composable
internal fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (selected) AmberCore else TextBright
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .glassSurface(CircleShape, lit = selected)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, color = tint, fontSize = CineType.Body, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * One capsule for screen size: [-] 85% [+]. Tap the percentage to jump back to 100%.
 * Same glass as the other controls, so it reads as part of the player.
 */
@Composable
internal fun GlassSizeCapsule(
    scale: Float,
    modifier: Modifier = Modifier,
    onSmaller: () -> Unit,
    onReset: () -> Unit,
    onBigger: () -> Unit,
) {
    val atFull = GlassesScreenSizePolicy.snap(scale) >= 1f
    val atSmallest = GlassesScreenSizePolicy.snap(scale) <= GlassesScreenSizePolicy.STEPS.last()
    Row(
        modifier = modifier
            .height(52.dp)
            .glassSurface(CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleButton(Icons.Rounded.Remove, "Smaller", enabled = !atSmallest, onClick = onSmaller)
        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.14f)))
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 76.dp)
                .height(52.dp)
                .clickable(role = Role.Button, onClick = onReset)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                GlassesScreenSizePolicy.label(scale),
                color = if (atFull) TextBright else AmberCore,
                fontSize = CineType.Body,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.14f)))
        CapsuleButton(Icons.Rounded.Add, "Bigger", enabled = !atFull, onClick = onBigger)
    }
}

@Composable
private fun CapsuleButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) TextBright else TextBright.copy(alpha = 0.3f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Seek bar in the player's neon-thread style: a dim line ahead, a glowing amber line behind
 * the playhead and a lit dot on it. Tap or drag; the dot grows while dragging.
 */
@Composable
internal fun GlassNeonSeekBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (dragging) 1f else 0.45f, tween(220), label = "glassSeekGlow")
    val dot by animateFloatAsState(if (dragging) 1.5f else 1f, tween(160), label = "glassSeekDot")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    var f = (down.position.x / w).coerceIn(0f, 1f)
                    dragging = true
                    onScrub(f)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        val c = e.changes.firstOrNull() ?: break
                        f = (c.position.x / w).coerceIn(0f, 1f)
                        if (!c.pressed) break
                        c.consume()
                        onScrub(f)
                    }
                    dragging = false
                    onCommit(f)
                }
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cy = size.height / 2f
            val tx = size.width * fraction.coerceIn(0f, 1f)
            val th = 2.6.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.20f), Offset(0f, cy), Offset(size.width, cy), th * 1.3f)
            drawLine(AmberGlow.copy(alpha = 0.25f * (0.5f + glow)), Offset(0f, cy), Offset(tx, cy), th * 4.2f)
            drawLine(AmberGlow, Offset(0f, cy), Offset(tx, cy), th * 1.3f)
            val c = Offset(tx, cy)
            drawCircle(AmberGlow.copy(alpha = 0.35f * glow + 0.15f), th * 3.2f * dot, c)
            drawCircle(Color(0xFFFFF3D6), th * 1.5f * dot, c)
        }
    }
}
