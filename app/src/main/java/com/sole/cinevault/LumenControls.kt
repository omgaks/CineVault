package com.sole.cinevault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Shared touch controls for Lumen windows: − / + steppers with hold-to-repeat and a bouncing
 * number, a tab strip, a hold-to-confirm button, and "ghost focus" (the window fades while a
 * finger is on a slider or stepper so the picture behind stays visible).
 */

/** How see-through a window gets while a control is being adjusted. */
internal const val LUMEN_GHOST_ALPHA = 0.35f

/** Shared flag a window provides; controls inside it switch it on while a finger is down. */
internal class LumenGhost {
    var active by mutableStateOf(false)
    var bubble by mutableStateOf<String?>(null)
}

internal val LocalLumenGhost = compositionLocalOf { LumenGhost() }

private fun cutShape(corner: Dp) = CutCornerShape(topStart = corner, topEnd = 0.dp, bottomEnd = corner, bottomStart = 0.dp)

/**
 * A 48 dp cut-corner − or + button. A press steps once straight away; holding repeats every 90 ms
 * after 380 ms, and speeds up (×2 after 5 steps, ×5 after 10). [onStep] gets the multiplier.
 */
@Composable
internal fun LumenStepButton(
    symbol: String,
    description: String,
    accent: Color,
    onStep: (Int) -> Unit,
) {
    val motion = rememberMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val ghost = LocalLumenGhost.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed && motion) 0.88f else 1f, tween(80), label = "stepScale")
    val shape = remember { cutShape(9.dp) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(accent.copy(alpha = if (pressed) 0.32f else 0.14f), shape)
            .border(1.5.dp, accent, shape)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { latest(1); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    ghost.active = true
                    val job = scope.launch {
                        latest(1)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        delay(380)
                        var n = 0
                        while (true) {
                            n++
                            latest(if (n > 10) 5 else if (n > 5) 2 else 1)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            delay(90)
                        }
                    }
                    try {
                        awaitRelease()
                    } finally {
                        job.cancel()
                        pressed = false
                        ghost.active = false
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** Big glowing number that pops to 1.28× and settles with a spring whenever it changes. */
@Composable
internal fun LumenBigNumber(text: String, unit: String, accent: Color) {
    val motion = rememberMotionEnabled()
    val bump = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        if (first) { first = false; return@LaunchedEffect }
        if (motion) {
            bump.snapTo(1.28f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            style = TextStyle(
                color = accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
                shadow = Shadow(accent.copy(alpha = 0.6f), blurRadius = 18f),
            ),
            modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value },
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(unit, color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

/**
 * Label on its own line, then − number +. [onStep] gets a signed amount in "steps": -1 for a tap on −,
 * +5 for a long hold on +, and so on.
 */
@Composable
internal fun LumenStepper(
    label: String,
    valueText: String,
    unit: String,
    accent: Color,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ghost = LocalLumenGhost.current
    LaunchedEffect(valueText, ghost.active) { if (ghost.active) ghost.bubble = valueText + (if (unit.isEmpty()) "" else " $unit") }
    Column(modifier = modifier.fillMaxWidth()) {
        LumenSectionLabel(label, accent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LumenStepButton("−", "$label minus", accent) { onStep(-it) }
            LumenBigNumber(valueText, unit, accent)
            LumenStepButton("+", "$label plus", accent) { onStep(it) }
        }
    }
}

internal data class LumenTab(val label: String, val icon: ImageVector)

/** Tab strip: icon over a 12 sp label (icon only on very narrow windows); the active tab glows. */
@Composable
internal fun LumenTabStrip(
    tabs: List<LumenTab>,
    selected: Int,
    accent: Color,
    iconOnly: Boolean,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEachIndexed { i, tab ->
            val on = i == selected
            val tint by animateColorAsState(if (on) accent else TextMuted, tween(180), label = "tabTint")
            val line by animateColorAsState(if (on) accent else Color.Transparent, tween(180), label = "tabLine")
            val fill by animateColorAsState(if (on) accent.copy(alpha = 0.16f) else Color.Transparent, tween(180), label = "tabFill")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(fill)
                    .drawBehind { drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx())) }
                    .semantics { contentDescription = tab.label; role = Role.Tab }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                if (!iconOnly) {
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = LumenType.caption,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Full-width cut-corner action button. */
@Composable
internal fun LumenActionButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = remember { cutShape(10.dp) }
    val a = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(accent.copy(alpha = 0.16f * a), shape)
            .border(1.5.dp, accent.copy(alpha = a), shape)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = accent.copy(alpha = a),
            fontSize = LumenType.body,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Hold-to-confirm button for things that wipe settings. Fill grows while you hold, rewinds if you let
 * go early, and fires once when full. Haptics ramp up as it fills.
 */
@Composable
internal fun LumenHoldButton(
    text: String,
    accent: Color,
    holdMs: Int = 1500,
    onConfirm: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onConfirm)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = remember { cutShape(10.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(accent.copy(alpha = 0.10f), shape)
            .drawBehind { drawRect(accent.copy(alpha = 0.35f), size = Size(size.width * progress.value, size.height)) }
            .border(1.5.dp, accent, shape)
            .semantics { role = Role.Button; contentDescription = "$text. Press and hold." }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        val steps = listOf(0.25f, 0.5f, 0.75f)
                        for (mark in steps) {
                            progress.animateTo(mark, tween((holdMs * 0.25f).toInt()))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        progress.animateTo(1f, tween((holdMs * 0.25f).toInt()))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latest()
                        progress.animateTo(0f, tween(250))
                    }
                    try {
                        awaitRelease()
                    } finally {
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(180)) }
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = accent,
            fontSize = LumenType.body,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
    }
}

/** Helper for windows that draw their own frame: provides ghost state and returns its animated alpha. */
@Composable
internal fun rememberLumenGhost(): Pair<LumenGhost, Float> {
    val ghost = remember { LumenGhost() }
    val alpha by animateFloatAsState(
        if (ghost.active) LUMEN_GHOST_ALPHA else 1f,
        tween(if (ghost.active) 120 else 250),
        label = "lumenGhostAlpha",
    )
    return ghost to alpha
}

/** Provide [ghost] to everything inside [content]. */
@Composable
internal fun LumenGhostScope(ghost: LumenGhost, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLumenGhost provides ghost, content = content)
}
