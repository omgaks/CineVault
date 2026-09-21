package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Canonical CineVault surface for Halo.
 *
 * D4-16 wires the canonical target activation policy into live click delivery.
 * Android/Compose still owns the real target hit test; this layer only prevents
 * a qualified Halo click from being dispatched when the current root surface
 * is unavailable/outside or when drag owns the gesture.
 */
@Composable
fun HaloCanonicalCineVaultSurface(
    enabled: Boolean = true,
    onActivity: (HaloActivityEvent) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coordinator = remember { HaloInteractionCoordinator() }
    val clock = remember { HaloEventClock() }
    val pulseController = remember { HaloClickPulseController() }
    val rootView = LocalView.current
    val syntheticGuard = remember { HaloSyntheticDispatchGuard() }
    val targetDispatcher = remember(rootView) { HaloCanonicalTargetDispatcher(rootView) }
    val dragDispatcher = remember(rootView, syntheticGuard) {
        HaloCanonicalDragDispatcher(rootView, syntheticGuard)
    }

    var haloPosition by remember { mutableStateOf<HaloVector?>(null) }
    var haloStable by remember { mutableStateOf(false) }
    var awareness by remember {
        mutableStateOf(
            HaloTargetAwareness(
                HaloTargetPresence.UNAVAILABLE,
                HaloFocusConfidence.NONE,
                false,
            )
        )
    }
    var pulseGeneration by remember { mutableStateOf(0) }
    var pulseProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pulseGeneration) {
        if (pulseGeneration == 0) return@LaunchedEffect
        while (true) {
            val progress = pulseController.progress(SystemClock.uptimeMillis())
            pulseProgress = progress
            if (progress <= 0f) break
            delay(16L)
        }
    }

    DisposableEffect(enabled, dragDispatcher) {
        onDispose {
            dragDispatcher.cancel()
            coordinator.reset()
            clock.reset()
            pulseController.reset()
            pulseProgress = 0f
            awareness = HaloTargetAwareness(
                HaloTargetPresence.UNAVAILABLE,
                HaloFocusConfidence.NONE,
                false,
            )
        }
    }

    HaloInputSurface(
        enabled = enabled,
        modifier = modifier.fillMaxSize(),
        onSample = { sample ->
            if (!syntheticGuard.isDispatching() &&
                !targetDispatcher.isDispatchingSyntheticClick()
            ) {
                val now = SystemClock.uptimeMillis()
                val frame = coordinator.update(
                    sample = sample,
                    eventTimeMillis = now,
                    deltaTimeMillis = clock.deltaMillis(now),
                )

                haloPosition = frame.position
                haloStable = frame.stability.isStable

                val currentAwareness = HaloTargetAwarenessResolver.resolve(
                    position = frame.position,
                    widthPx = rootView.width,
                    heightPx = rootView.height,
                    focusConfidence = frame.focusConfidence,
                )
                awareness = currentAwareness

                frame.activity?.let(onActivity)
                frame.dragEvents.forEach(dragDispatcher::dispatch)

                val dragging = frame.dragEvents.isNotEmpty()

                frame.clickEvents
                    .firstOrNull { it.type == HaloClickEventType.CLICK }
                    ?.let { click ->
                        val decision = HaloTargetActivationPolicy.decide(
                            awareness = currentAwareness,
                            clickQualified = true,
                            dragging = dragging,
                        )

                        if (decision == HaloTargetActivationDecision.DISPATCH) {
                            if (click.feedback.visualPulse) {
                                pulseController.trigger(now)
                                pulseProgress = 1f
                                pulseGeneration += 1
                            }
                            targetDispatcher.dispatchClick(click.position)
                        }
                    }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (enabled) {
                haloPosition?.let {
                    HaloCursor(
                        position = it,
                        stable = haloStable,
                        awareness = awareness,
                        pulseProgress = pulseProgress,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HaloCursor(
    position: HaloVector,
    stable: Boolean,
    awareness: HaloTargetAwareness,
    pulseProgress: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val normalRadius = with(density) { 9.dp.toPx() }
    val precisionRadius = with(density) { 6.dp.toPx() }
    val normalRing = with(density) { 15.dp.toPx() }
    val precisionRing = with(density) { 12.dp.toPx() }
    val confidenceRing = with(density) { 18.dp.toPx() }
    val pulseExpansion = with(density) { 8.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val tick = with(density) { 4.dp.toPx() }

    Canvas(modifier) {
        val centre = Offset(
            position.x.coerceIn(0f, 1f) * size.width,
            position.y.coerceIn(0f, 1f) * size.height,
        )
        val radius = if (stable) precisionRadius else normalRadius

        drawCircle(
            Color.White.copy(alpha = if (stable) 1f else .95f),
            radius,
            centre,
        )
        drawCircle(
            Color.Black.copy(alpha = .72f),
            radius,
            centre,
            style = Stroke(stroke),
        )

        if (stable) {
            drawCircle(
                Color.White.copy(alpha = .78f),
                precisionRing,
                centre,
                style = Stroke(stroke),
            )

            val confidence =
                if (awareness.canPresentFocusFeedback) awareness.focusConfidence
                else HaloFocusConfidence.NONE

            val alpha = when (confidence) {
                HaloFocusConfidence.NONE -> 0f
                HaloFocusConfidence.SETTLING -> .45f
                HaloFocusConfidence.READY -> .72f
                HaloFocusConfidence.STRONG -> 1f
            }

            if (alpha > 0f) {
                val c = Color.White.copy(alpha = alpha)
                drawLine(
                    c,
                    Offset(centre.x - precisionRing - tick, centre.y),
                    Offset(centre.x - precisionRing, centre.y),
                    stroke,
                )
                drawLine(
                    c,
                    Offset(centre.x + precisionRing, centre.y),
                    Offset(centre.x + precisionRing + tick, centre.y),
                    stroke,
                )
                drawLine(
                    c,
                    Offset(centre.x, centre.y - precisionRing - tick),
                    Offset(centre.x, centre.y - precisionRing),
                    stroke,
                )
                drawLine(
                    c,
                    Offset(centre.x, centre.y + precisionRing),
                    Offset(centre.x, centre.y + precisionRing + tick),
                    stroke,
                )
            }

            if (
                confidence == HaloFocusConfidence.READY ||
                confidence == HaloFocusConfidence.STRONG
            ) {
                val confidenceAlpha =
                    if (confidence == HaloFocusConfidence.STRONG) .58f else .30f
                drawCircle(
                    Color.White.copy(alpha = confidenceAlpha),
                    confidenceRing,
                    centre,
                    style = Stroke(stroke),
                )
            }
        }

        val pulse = pulseProgress.coerceIn(0f, 1f)
        if (pulse > 0f) {
            val base = if (stable) precisionRing else normalRing
            drawCircle(
                Color.White.copy(alpha = .55f * pulse),
                base + pulseExpansion * (1f - pulse),
                centre,
                style = Stroke(stroke),
            )
        }
    }
}

internal class HaloEventClock {
    private var previousMillis: Long? = null

    fun deltaMillis(nowMillis: Long): Long {
        val previous = previousMillis
        previousMillis = nowMillis
        return if (previous == null) {
            16L
        } else {
            (nowMillis - previous).coerceAtLeast(1L)
        }
    }

    fun reset() {
        previousMillis = null
    }
}
