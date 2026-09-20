package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * D2-1 — universal Halo input surface.
 *
 * This is an additive, transparent input layer. It does not draw or replace
 * CineVault UI. The complete available surface participates in Halo movement.
 *
 * IMPORTANT:
 * D2-1 observes pointer motion at PointerEventPass.Final and intentionally does
 * not consume events. Existing CineVault buttons, cards, scrolling and player
 * gestures therefore remain authoritative while Halo learns the same input.
 * Click injection / drag ownership comes in later D2 slices.
 */
@Composable
fun HaloInputSurface(
    enabled: Boolean,
    onSample: (HaloPointerSample) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()

        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(
                                    pass = PointerEventPass.Final,
                                )

                                val change = event.changes.firstOrNull() ?: continue
                                val size = this@pointerInput.size

                                onSample(
                                    HaloPointerMapper.sample(
                                        xPx = change.position.x,
                                        yPx = change.position.y,
                                        widthPx = size.width,
                                        heightPx = size.height,
                                        pressed = change.pressed,
                                    )
                                )
                            }
                        }
                    }
            )
        }
    }
}

data class HaloPointerSample(
    /** Raw position in the current touch surface. */
    val xPx: Float,
    val yPx: Float,

    /** Normalised 0..1 coordinates across 100% of the available surface. */
    val xFraction: Float,
    val yFraction: Float,

    val pressed: Boolean,
)

object HaloPointerMapper {

    fun sample(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        pressed: Boolean,
    ): HaloPointerSample {
        require(widthPx > 0) { "Halo surface width must be greater than zero." }
        require(heightPx > 0) { "Halo surface height must be greater than zero." }

        val boundedX = xPx.coerceIn(0f, widthPx.toFloat())
        val boundedY = yPx.coerceIn(0f, heightPx.toFloat())

        return HaloPointerSample(
            xPx = boundedX,
            yPx = boundedY,
            xFraction = boundedX / widthPx.toFloat(),
            yFraction = boundedY / heightPx.toFloat(),
            pressed = pressed,
        )
    }
}
