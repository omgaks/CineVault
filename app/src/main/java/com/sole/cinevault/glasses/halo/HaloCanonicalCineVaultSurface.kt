package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

/**
 * Canonical CineVault surface for Halo.
 *
 * D4-4 exposes D4 precision stability visually without creating a second UI
 * or changing click/drag semantics. The normal Halo remains compact; once it
 * settles over a target it becomes a tighter precision reticle.
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
    val rootView = LocalView.current
    val syntheticGuard = remember { HaloSyntheticDispatchGuard() }

    val targetDispatcher = remember(rootView) {
        HaloCanonicalTargetDispatcher(rootView)
    }
    val dragDispatcher = remember(rootView, syntheticGuard) {
        HaloCanonicalDragDispatcher(
            rootView = rootView,
            syntheticDispatchGuard = syntheticGuard,
        )
    }

    var haloPosition by remember { mutableStateOf<HaloVector?>(null) }
    var haloStable by remember { mutableStateOf(false) }
    var clickPulse by remember { mutableStateOf(0) }

    DisposableEffect(enabled, dragDispatcher) {
        onDispose {
            dragDispatcher.cancel()
            coordinator.reset()
            clock.reset()
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
                frame.activity?.let(onActivity)

                frame.dragEvents.forEach(dragDispatcher::dispatch)

                frame.clickEvents
                    .firstOrNull { it.type == HaloClickEventType.CLICK }
                    ?.let { click ->
                        clickPulse += 1
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
                        pulseKey = clickPulse,
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
    pulseKey: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val normalRadiusPx = with(density) { 9.dp.toPx() }
    val precisionRadiusPx = with(density) { 6.dp.toPx() }
    val normalRingPx = with(density) { 15.dp.toPx() }
    val precisionRingPx = with(density) { 12.dp.toPx() }
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
            val ringRadius = precisionRingPx
            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = ringRadius,
                center = centre,
                style = Stroke(width = strokePx),
            )

            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(centre.x - ringRadius - precisionTickPx, centre.y),
                end = Offset(centre.x - ringRadius, centre.y),
                strokeWidth = strokePx,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(centre.x + ringRadius, centre.y),
                end = Offset(centre.x + ringRadius + precisionTickPx, centre.y),
                strokeWidth = strokePx,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(centre.x, centre.y - ringRadius - precisionTickPx),
                end = Offset(centre.x, centre.y - ringRadius),
                strokeWidth = strokePx,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(centre.x, centre.y + ringRadius),
                end = Offset(centre.x, centre.y + ringRadius + precisionTickPx),
                strokeWidth = strokePx,
            )
        }

        if (pulseKey > 0) {
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = if (stable) precisionRingPx else normalRingPx,
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
