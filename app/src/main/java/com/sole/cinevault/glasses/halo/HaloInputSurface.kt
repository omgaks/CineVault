package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * D2-9 — universal Halo input observer.
 *
 * IMPORTANT:
 * The observer now lives on the PARENT Box rather than on a full-screen sibling
 * overlay. That keeps the real CineVault child in the normal Compose hit path,
 * so buttons/cards/player controls remain the actual interaction targets.
 *
 * We observe at PointerEventPass.Final and never consume changes.
 */
@Composable
fun HaloInputSurface(
    enabled: Boolean,
    onSample: (HaloPointerSample) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haloModifier = if (enabled) {
        modifier.pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Final)
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
    } else {
        modifier
    }

    Box(modifier = haloModifier) {
        content()
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
