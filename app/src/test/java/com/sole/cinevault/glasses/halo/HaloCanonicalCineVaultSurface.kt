package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 * D2-9 — visible Halo + canonical target dispatch.
 *
 * CineVaultRoot remains the real UI. Halo only:
 *  1. observes the shared input stream,
 *  2. draws the cursor,
 *  3. routes a classified CLICK through Android's normal hit-test pipeline.
 *
 * There is no glasses-only button registry and no duplicate CineVault UI.
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
    val targetDispatcher = remember(rootView) {
        HaloCanonicalTargetDispatcher(rootView)
    }

    var haloPosition by remember { mutableStateOf<HaloVector?>(null) }
    var clickPulse by remember { mutableStateOf(0) }

    HaloInputSurface(
        enabled = enabled,
        modifier = modifier.fillMaxSize(),
        onSample = { sample ->
            // Synthetic click events come back through the same Android root.
            // Ignore them here so a Halo click cannot recursively create itself.
            if (!targetDispatcher.isDispatchingSyntheticClick()) {
                val now = SystemClock.uptimeMillis()
                val frame = coordinator.update(
                    sample = sample,
                    eventTimeMillis = now,
                    deltaTimeMillis = clock.deltaMillis(now),
                )

                haloPosition = frame.position
                frame.activity?.let(onActivity)

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
    pulseKey: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 9.dp.toPx() }
    val ringPx = with(density) { 15.dp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }

    Canvas(modifier = modifier) {
        val centre = Offset(
            x = position.x.coerceIn(0f, 1f) * size.width,
            y = position.y.coerceIn(0f, 1f) * size.height,
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = radiusPx,
            center = centre,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.72f),
            radius = radiusPx,
            center = centre,
            style = Stroke(width = strokePx),
        )

        if (pulseKey > 0) {
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = ringPx,
                center = centre,
                style = Stroke(width = strokePx),
            )
        }
    }
}

/**
 * Keeps event timing local to the Halo surface and avoids a frame-rate
 * assumption in HaloMotionEngine.
 */
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
