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
                            val y = size.height * p
                            drawLine(accent.copy(alpha = 1f - p), Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
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
