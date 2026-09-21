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
 * D4-11 consumes canonical focus confidence in the visible Halo itself.
 * Confidence changes only the reticle treatment; it does not snap, auto-click,
 * capture Compose focus, or alter gesture ownership.
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
        HaloCanonicalDragDispatcher(
            rootView = rootView,
            syntheticDispatchGuard = syntheticGuard,
        )
    }

    var haloPosition by remember { mutableStateOf<HaloVector?>(null) }
    var haloStable by remember { mutableStateOf(false) }
    var focusConfidence by remember { mutableStateOf(HaloFocusConfidence.NONE) }
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
            focusConfidence = HaloFocusConfidence.NONE
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
                focusConfidence = frame.focusConfidence
                frame.activity?.let(onActivity)

                frame.dragEvents.forEach(dragDispatcher::dispatch)

                frame.clickEvents
                    .firstOrNull { it.type == HaloClickEventType.CLICK }
                    ?.let { click ->
                        if (click.feedback.visualPulse) {
                            pulseController.trigger(now)
                            pulseProgress = 1f
                            pulseGeneration += 1
                        }
                        targetDispatcher.dispatchClick(click.position)
                    }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (enabled) {
                haloPosition?.let { position ->
                    HaloCursor(
                        position = position,
                        stable = haloStable,
                        focusConfidence = focusConfidence,
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
    focusConfidence: HaloFocusConfidence,
    pulseProgress: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val normalRadiusPx = with(density) { 9.dp.toPx() }
    val precisionRadiusPx = with(density) { 6.dp.toPx() }
    val normalRingPx = with(density) { 15.dp.toPx() }
    val precisionRingPx = with(density) { 12.dp.toPx() }
    val confidenceRingPx = with(density) { 18.dp.toPx() }
    val pulseExpansionPx = with(density) { 8.dp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }
    val precisionTickPx = with(density) { 4.dp.toPx() }

    Canvas(modifier = modifier) {
        val centre = Offset(
            x = position.x.coerceIn(0f, 1f) * size.width,
            y = position.y.coerceIn(0f, 1f) * size.height,
        )
        val radius = if (stable) precisionRadiusPx else normalRadiusPx

        drawCircle(
            color = Color.White.copy(alpha = if (stable) 1f else 0.95f),
            radius = radius,
            center = centre,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.72f),
            radius = radius,
            center = centre,
            style = Stroke(width = strokePx),
        )

        if (stable) {
            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = precisionRingPx,
                center = centre,
                style = Stroke(width = strokePx),
            )

            val tickAlpha = when (focusConfidence) {
                HaloFocusConfidence.NONE -> 0f
                HaloFocusConfidence.SETTLING -> 0.45f
                HaloFocusConfidence.READY -> 0.72f
                HaloFocusConfidence.STRONG -> 1f
            }

            if (tickAlpha > 0f) {
                val tickColor = Color.White.copy(alpha = tickAlpha)
                drawLine(tickColor, Offset(centre.x - precisionRingPx - precisionTickPx, centre.y),
                    Offset(centre.x - precisionRingPx, centre.y), strokePx)
                drawLine(tickColor, Offset(centre.x + precisionRingPx, centre.y),
                    Offset(centre.x + precisionRingPx + precisionTickPx, centre.y), strokePx)
                drawLine(tickColor, Offset(centre.x, centre.y - precisionRingPx - precisionTickPx),
                    Offset(centre.x, centre.y - precisionRingPx), strokePx)
                drawLine(tickColor, Offset(centre.x, centre.y + precisionRingPx),
                    Offset(centre.x, centre.y + precisionRingPx + precisionTickPx), strokePx)
            }

            if (focusConfidence == HaloFocusConfidence.READY ||
                focusConfidence == HaloFocusConfidence.STRONG
            ) {
                val confidenceAlpha =
                    if (focusConfidence == HaloFocusConfidence.STRONG) 0.58f else 0.30f
                drawCircle(
                    color = Color.White.copy(alpha = confidenceAlpha),
                    radius = confidenceRingPx,
                    center = centre,
                    style = Stroke(width = strokePx),
                )
            }
        }

        val pulse = pulseProgress.coerceIn(0f, 1f)
        if (pulse > 0f) {
            val baseRing = if (stable) precisionRingPx else normalRingPx
            val expansion = pulseExpansionPx * (1f - pulse)
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * pulse),
                radius = baseRing + expansion,
                center = centre,
                style = Stroke(width = strokePx),
            )
        }
    }
}

internal class HaloEventClock {
    private var previousMillis: Long? = null

    fun deltaMillis(nowMillis: Long): Long {
        val previous = previousMillis
        previousMillis = nowMillis
        return if (previous == null) 16L
        else (nowMillis - previous).coerceAtLeast(1L)
    }

    fun reset() {
        previousMillis = null
    }
}
