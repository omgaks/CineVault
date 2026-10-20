package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/*
 * Lumen Window: the one frame every player menu window uses ("Style A, Neon Cut" with the
 * "M2 Signal" motion from the Lumen Windows design).
 *
 * Law: windows live on the RIGHT RAIL. They sit between the transport bar and the right pill
 * column, rise from the bottom, leave a gap above the seek bar, and never cover the right pills.
 * They are semi transparent, share one type scale, glow in the colour of the pill that opened
 * them, and scale to the space available. Closing is done by the player's tap-on-empty-space layer
 * and by tapping the window's own pill again, never by touching the window itself.
 */

/** S, M, L windows. Width is a share of the screen, clamped, so a phone and a tablet both fit. */
internal enum class LumenSize(val fraction: Float, val minDp: Float, val maxDp: Float) {
    S(0.22f, 200f, 240f),
    M(0.26f, 240f, 290f),
    L(0.30f, 280f, 340f),
}

/** One type scale for every window. */
internal object LumenType {
    val title = 14.sp
    val section = 11.sp
    val body = 13.sp
    val value = 15.sp
    val caption = 10.5.sp
}

/** Pure rail maths, kept free of Compose so it can be unit tested. */
internal object LumenRail {
    private const val FALLBACK_RIGHT_COLUMN_DP = 120f
    private const val GAP_TO_PILLS_DP = 8f
    private const val MIN_WINDOW_HEIGHT_DP = 120f

    fun widthDp(screenWidthDp: Float, size: LumenSize): Float {
        val wanted = screenWidthDp * size.fraction
        val clamped = wanted.coerceIn(size.minDp, size.maxDp)
        // Never wider than 60% of a very narrow screen.
        return clamped.coerceAtMost(screenWidthDp * 0.6f)
    }

    fun endInsetDp(pillEndDp: Float, rightColumnWidthDp: Float?): Float =
        pillEndDp + (rightColumnWidthDp ?: FALLBACK_RIGHT_COLUMN_DP) + GAP_TO_PILLS_DP

    fun maxHeightDp(screenHeightDp: Float, topInsetDp: Float, bottomInsetDp: Float): Float =
        (screenHeightDp - topInsetDp - bottomInsetDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
}

internal val LumenShape = CutCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 18.dp, bottomStart = 0.dp)
private val LumenBadgeShape = CutCornerShape(topStart = 7.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 0.dp)
private val LumenChipShape = CutCornerShape(topStart = 6.dp, topEnd = 0.dp, bottomEnd = 6.dp, bottomStart = 0.dp)

/**
 * The window itself. Place it inside the player's full-screen Box, ABOVE the tap-outside layer.
 * [bottomInset] is the space to keep free at the bottom (transport bar plus seek bar plus a gap).
 */
@Composable
internal fun BoxScope.LumenWindow(
    visible: Boolean,
    accent: Color,
    title: String,
    icon: ImageVector?,
    size: LumenSize,
    isLandscape: Boolean,
    bottomInset: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberMotionEnabled()
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val width = LumenRail.widthDp(maxWidth.value, size).dp
        val rightColumn = PlayerPillMetrics.rightColumnWidth
        val endInset = LumenRail.endInsetDp(
            PlayerPillMetrics.end.value,
            if (rightColumn.isSpecified) rightColumn.value else null,
        ).dp
        val topInset = PlayerPillMetrics.top(isLandscape)
        val maxH = LumenRail.maxHeightDp(maxHeight.value, topInset.value, bottomInset.value).dp

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endInset, bottom = bottomInset),
            enter = if (motion) {
                scaleIn(initialScale = 0.2f, transformOrigin = TransformOrigin(1f, 1f), animationSpec = tween(320)) +
                    fadeIn(tween(200))
            } else fadeIn(tween(120)),
            exit = if (motion) {
                scaleOut(targetScale = 0.4f, transformOrigin = TransformOrigin(1f, 1f), animationSpec = tween(160)) +
                    fadeOut(tween(160))
            } else fadeOut(tween(100)),
        ) {
            val wipe = remember { Animatable(if (motion) 0f else 1f) }
            LaunchedEffect(Unit) { if (motion) wipe.animateTo(1f, tween(520)) }

            Column(
                modifier = Modifier
                    .width(width)
                    .heightIn(max = maxH)
                    .shadow(14.dp, LumenShape, ambientColor = accent, spotColor = accent)
                    .background(Color(0x9E080A10), LumenShape)
                    .border(
                        1.6.dp,
                        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), accent)),
                        LumenShape,
                    )
                    .scanlines(0.035f)
                    .drawWithContent {
                        drawContent()
                        val p = wipe.value
                        if (p < 1f) {
                            val y = this.size.height * p
                            drawLine(accent.copy(alpha = 1f - p), Offset(0f, y), Offset(this.size.width, y), 3.dp.toPx())
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                LumenHeader(title = title, icon = icon, accent = accent)
                Spacer(Modifier.size(10.dp))
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun LumenHeader(title: String, icon: ImageVector?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(accent.copy(alpha = 0.16f), LumenBadgeShape)
                    .border(1.3.dp, accent, LumenBadgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title.uppercase(),
            color = TextBright,
            fontSize = LumenType.title,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp,
        )
    }
}

/** Small coloured heading with a fading rule, used to group things inside a window. */
@Composable
internal fun LumenSectionLabel(text: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), color = accent, fontSize = LumenType.section, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent)))
        )
    }
}

/** One tappable choice: a bar in the accent colour when selected. */
@Composable
internal fun LumenOptionRow(label: String, selected: Boolean, accent: Color, trailing: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f), LumenChipShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 16.dp).background(if (selected) accent else accent.copy(alpha = 0.35f)))
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = if (selected) TextBright else TextMuted.copy(alpha = 0.95f),
            fontSize = LumenType.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, color = accent, fontSize = LumenType.caption, fontWeight = FontWeight.Bold)
    }
}


/** Violet used by Sound FX; other windows pass their own pill colour. */
internal val LumenViolet = Color(0xFFC792FF)

/** Cut-corner on/off switch in the window's accent colour. */
@Composable
internal fun LumenSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, accent: Color = LumenViolet) {
    val shape = CutCornerShape(topStart = 5.dp, topEnd = 0.dp, bottomEnd = 5.dp, bottomStart = 0.dp)
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, tween(180), label = "lumenSwitch")
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 20.dp)
            .background(if (checked) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f), shape)
            .border(1.2.dp, if (checked) accent else accent.copy(alpha = 0.45f), shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 3.dp)
                .size(14.dp)
                .background(if (checked) TextBright else TextMuted, CutCornerShape(topStart = 3.dp, topEnd = 0.dp, bottomEnd = 3.dp, bottomStart = 0.dp))
        )
    }
}

/** A choice chip: filled and glowing when selected. */
@Composable
internal fun LumenChip(text: String, selected: Boolean, accent: Color = LumenViolet, onClick: () -> Unit) {
    Text(
        text = text.uppercase(),
        color = if (selected) Color(0xFF05060A) else TextBright,
        fontSize = LumenType.caption,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .background(if (selected) accent else Color.Transparent, LumenChipShape)
            .border(1.2.dp, accent.copy(alpha = if (selected) 1f else 0.6f), LumenChipShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Waveform slider: the part you have filled is a live wave (it ripples while you drag), the rest is
 * a flat dim line, and the handle is a diamond. Standing still it is calm; reduced motion freezes it.
 */
@Composable
internal fun LumenSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    accent: Color = LumenViolet,
) {
    val motion = rememberMotionEnabled()
    var dragging by remember { mutableStateOf(false) }
    val phase by rememberLoop(enabled = motion, from = 0f, to = 6.2832f, periodMs = if (dragging) 700 else 2600)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val latestChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)

    fun setFrom(x: Float, width: Float) {
        val f = (x / width).coerceIn(0f, 1f)
        latestChange(valueRange.start + f * span)
    }

    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(valueRange) {
                detectTapGestures(onTap = { setFrom(it.x, size.width.toFloat()) })
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { dragging = true; setFrom(it.x, size.width.toFloat()) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    setFrom(change.position.x, size.width.toFloat())
                }
            },
    ) {
        val cy = size.height / 2f
        val tx = size.width * fraction
        val inset = 6.dp.toPx()
        // Flat dim line for the part still to fill.
        drawLine(Color.White.copy(alpha = 0.25f), Offset(tx, cy), Offset(size.width, cy), 2.dp.toPx())
        // Live wave for the filled part.
        if (tx > 1f) {
            val amp = (if (dragging) 5.dp else 3.dp).toPx()
            val path = Path()
            var x = 0f
            val step = 3.dp.toPx()
            path.moveTo(0f, cy)
            while (x <= tx) {
                val fade = (x / (tx.coerceAtLeast(1f))).coerceIn(0f, 1f)
                path.lineTo(x, cy + kotlin.math.sin(x / 7.dp.toPx() * 1f + phase) * amp * fade)
                x += step
            }
            drawPath(path, accent.copy(alpha = 0.30f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        // Diamond handle.
        val d = (if (dragging) 8.dp else 6.dp).toPx()
        val diamond = Path().apply {
            moveTo(tx, cy - d); lineTo(tx + d, cy); lineTo(tx, cy + d); lineTo(tx - d, cy); close()
        }
        drawPath(diamond, TextBright)
        drawPath(diamond, Color(0xFFFF6E8C), style = Stroke(width = 1.5.dp.toPx()))
    }
}


/** Card corner used for rows and tiles inside Lumen windows. */
internal val LumenCardShape = CutCornerShape(topStart = 10.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp)

/** The frame look (glow, glass, neon edge, scanlines) for windows that still draw their own content. */
internal fun Modifier.lumenFrame(accent: Color): Modifier = this
    .shadow(14.dp, LumenShape, ambientColor = accent, spotColor = accent)
    .background(Color(0x9E080A10), LumenShape)
    .border(1.6.dp, Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), accent)), LumenShape)
    .scanlines(0.035f)

/** Distance from the screen's right edge to the rail: pill column plus a small gap. */
@Composable
internal fun lumenRailEndInset(): Dp {
    val rc = PlayerPillMetrics.rightColumnWidth
    return LumenRail.endInsetDp(PlayerPillMetrics.end.value, if (rc.isSpecified) rc.value else null).dp
}

internal val LumenRose = Color(0xFFFF6E8C)
internal val LumenSky = Color(0xFF6FC3FF)
internal val LumenMint = Color(0xFF7CE0C3)
internal val LumenGold = Color(0xFFFFC94D)
