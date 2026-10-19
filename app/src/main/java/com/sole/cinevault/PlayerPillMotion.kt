package com.sole.cinevault

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

/*
 * Motion helpers for the floating player pills. Everything here is off when the person
 * switches on "Reduce player motion" in Settings or turns Android's animations off.
 */

private const val MOTION_PREFS = "cinevault_ui_motion"
private const val KEY_REDUCE_MOTION = "reduce_player_motion"

internal fun loadReduceMotion(context: Context): Boolean =
    context.getSharedPreferences(MOTION_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REDUCE_MOTION, false)

internal fun saveReduceMotion(context: Context, value: Boolean) {
    context.getSharedPreferences(MOTION_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_REDUCE_MOTION, value).apply()
}

/** False when motion should stay off (the app setting, or the system "remove animations"). */
@Composable
internal fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val systemOff = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
        !(systemOff || loadReduceMotion(context))
    }
}

/** A value that eases back and forth between [from] and [to]; sits at [rest] when [enabled] is false. */
@Composable
internal fun rememberLoop(
    enabled: Boolean,
    from: Float,
    to: Float,
    periodMs: Int,
    rest: Float = from,
    phaseMs: Int = 0,
): State<Float> {
    if (!enabled) return remember(rest) { mutableFloatStateOf(rest) }
    val transition = rememberInfiniteTransition(label = "pillLoop")
    return transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseMs),
        ),
        label = "pillLoopValue",
    )
}

/** A value that runs 0 to 1 over [periodMs] and starts over; sits at 0 when [enabled] is false. */
@Composable
internal fun rememberCycle(enabled: Boolean, periodMs: Int): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "pillCycle")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pillCycleValue",
    )
}

/**
 * A one-off kick: animates 1 back to 0 each time [trigger] changes (not on first show).
 * Use the value as the strength of a bounce, wiggle or shrink.
 */
@Composable
internal fun rememberKick(trigger: Any?, enabled: Boolean, durationMs: Int = 420): State<Float> {
    val value = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (!enabled) return@LaunchedEffect
        value.snapTo(1f)
        value.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return value.asState()
}

/** Draws a light running round the pill's edge ([runner]) and/or a sweep showing progress ([progress]). */
internal fun Modifier.pillBorderFx(
    color: Color,
    runner: (() -> Float)?,
    progress: (() -> Float)?,
    strokeWidthPx: Float,
): Modifier = if (runner == null && progress == null) this else drawWithContent {
    drawContent()
    val inset = strokeWidthPx / 2f
    val radius = (size.height - strokeWidthPx) / 2f
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                inset, inset, size.width - inset, size.height - inset,
                CornerRadius(radius, radius),
            )
        )
    }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    progress?.let { p ->
        val seg = Path()
        measure.getSegment(0f, length * p().coerceIn(0f, 1f), seg, true)
        drawPath(seg, color.copy(alpha = 0.95f), style = stroke)
    }
    runner?.let { r ->
        val start = r() * length
        val span = length * 0.16f
        val seg = Path()
        if (start + span <= length) {
            measure.getSegment(start, start + span, seg, true)
        } else {
            measure.getSegment(start, length, seg, true)
            measure.getSegment(0f, start + span - length, seg, true)
        }
        drawPath(seg, color, style = stroke)
    }
}
