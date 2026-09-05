package com.sole.cinevault.subtitles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Waveform slider (Delay) ─────────────────────────────────────────────
// Draws amplitude bars from an optional speech-activity timeline so the
// Delay thumb sits on top of a real "here's where the dialogue is" guide
// instead of a blind line. `speechTimeline` is nullable: before an
// Auto-Sync full scan has produced one, this renders as a plain dot-thumb
// track with no fabricated bars — never bars drawn from data that doesn't
// exist yet.
//
// The moment `speechTimeline` transitions from null to real data, the bars
// pulse (opacity sweep, ~3 cycles) before settling to steady state, so the
// "delay slider became a waveform" moment is actually visible instead of
// just silently appearing between one recomposition and the next.
@Composable
internal fun WaveformSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    speechTimeline: FloatArray?,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var lastBucket by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }

    var justBecameReady by remember { mutableStateOf(false) }
    var wasNullBefore by remember { mutableStateOf(speechTimeline == null) }
    LaunchedEffect(speechTimeline != null) {
        if (wasNullBefore && speechTimeline != null) {
            justBecameReady = true
        }
        wasNullBefore = speechTimeline == null
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_ready_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    LaunchedEffect(justBecameReady) {
        if (justBecameReady) {
            kotlinx.coroutines.delay(1500)
            justBecameReady = false
        }
    }

    fun fractionFor(x: Float) = (x / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun valueFor(fraction: Float) = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .pointerInput(speechTimeline, valueRange) {
                detectDragGestures(
                    onDragStart = { lastBucket = -1 },
                    onDrag = { change, _ ->
                        val fraction = fractionFor(change.position.x)
                        onValueChange(valueFor(fraction))
                        if (speechTimeline != null && speechTimeline.isNotEmpty()) {
                            val bucket = (fraction * speechTimeline.size).toInt().coerceIn(0, speechTimeline.size - 1)
                            val active = speechTimeline[bucket] > 0.5f
                            val wasActive = lastBucket >= 0 && speechTimeline[lastBucket] > 0.5f
                            if (bucket != lastBucket && active != wasActive) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            lastBucket = bucket
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (speechTimeline != null && speechTimeline.isNotEmpty()) {
            val barAlphaMultiplier = if (justBecameReady) pulseAlpha else 1f
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val barCount = speechTimeline.size
                val barWidth = (size.width / barCount) * 0.6f
                val gap = (size.width / barCount) - barWidth
                speechTimeline.forEachIndexed { i, amp ->
                    val h = (size.height * amp.coerceIn(0.05f, 1f))
                    val baseAlpha = if (amp > 0.5f) 0.85f else 0.25f
                    drawRect(
                        color = AmberGlow.copy(alpha = (baseAlpha * barAlphaMultiplier).coerceIn(0f, 1f)),
                        topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2f),
                        size = Size(barWidth, h)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
        val thumbFraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbFraction * widthPx).roundToInt() - with(density) { 7.dp.roundToPx() }, 0) }
                .size(14.dp)
                .clip(CircleShape)
                .background(AmberCore)
                .border(2.dp, Color(0xFF1A1206), CircleShape)
        )
    }
}
