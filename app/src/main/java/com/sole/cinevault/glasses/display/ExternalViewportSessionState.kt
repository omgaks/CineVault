package com.sole.cinevault.glasses.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class ExternalViewportTransform(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

internal object ExternalViewportSessionState {
    private const val MIN_SCALE = 0.75f
    private const val MAX_SCALE = 3f

    var transform by mutableStateOf(ExternalViewportTransform())
        private set

    fun applyGesture(
        zoom: Float,
        panX: Float,
        panY: Float,
    ) {
        val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nextScale = (transform.scale * safeZoom).coerceIn(MIN_SCALE, MAX_SCALE)

        transform =
            ExternalViewportTransform(
                scale = nextScale,
                panX = transform.panX + panX.takeIf(Float::isFinite).orZero(),
                panY = transform.panY + panY.takeIf(Float::isFinite).orZero(),
            )
    }

    fun reset() {
        transform = ExternalViewportTransform()
    }

    private fun Float?.orZero(): Float = this ?: 0f
}
