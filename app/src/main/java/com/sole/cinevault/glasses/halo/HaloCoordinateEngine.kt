package com.sole.cinevault.glasses.halo

/**
 * D2-2 — touch-surface -> rendered CineVault display coordinate engine.
 *
 * D2-1 produces normalised 0..1 samples from the entire input surface.
 * This engine projects those samples into the actual external render viewport.
 *
 * No device names or fixed resolutions are used. Mapping is based only on the
 * available viewport and optional content bounds, so later orientation/window
 * changes can supply fresh geometry without changing Halo logic.
 */
object HaloCoordinateEngine {

    fun map(
        sample: HaloPointerSample,
        viewport: HaloViewport,
    ): HaloDisplayPoint {
        val bounds = viewport.contentBounds ?: HaloContentBounds.full(viewport)

        val x = bounds.leftPx + sample.xFraction.coerceIn(0f, 1f) * bounds.widthPx
        val y = bounds.topPx + sample.yFraction.coerceIn(0f, 1f) * bounds.heightPx

        return HaloDisplayPoint(
            xPx = x.coerceIn(bounds.leftPx, bounds.rightPx),
            yPx = y.coerceIn(bounds.topPx, bounds.bottomPx),
            xFraction = sample.xFraction.coerceIn(0f, 1f),
            yFraction = sample.yFraction.coerceIn(0f, 1f),
            pressed = sample.pressed,
        )
    }
}

data class HaloViewport(
    val widthPx: Int,
    val heightPx: Int,
    val contentBounds: HaloContentBounds? = null,
) {
    init {
        require(widthPx > 0) { "Halo viewport width must be greater than zero." }
        require(heightPx > 0) { "Halo viewport height must be greater than zero." }

        contentBounds?.let { bounds ->
            require(bounds.leftPx >= 0f && bounds.topPx >= 0f) {
                "Halo content bounds cannot start outside the viewport."
            }
            require(bounds.rightPx <= widthPx.toFloat()) {
                "Halo content bounds exceed viewport width."
            }
            require(bounds.bottomPx <= heightPx.toFloat()) {
                "Halo content bounds exceed viewport height."
            }
        }
    }
}

data class HaloContentBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
) {
    init {
        require(rightPx > leftPx) { "Halo content width must be positive." }
        require(bottomPx > topPx) { "Halo content height must be positive." }
    }

    val widthPx: Float get() = rightPx - leftPx
    val heightPx: Float get() = bottomPx - topPx

    companion object {
        fun full(viewport: HaloViewport) = HaloContentBounds(
            leftPx = 0f,
            topPx = 0f,
            rightPx = viewport.widthPx.toFloat(),
            bottomPx = viewport.heightPx.toFloat(),
        )
    }
}

data class HaloDisplayPoint(
    val xPx: Float,
    val yPx: Float,
    val xFraction: Float,
    val yFraction: Float,
    val pressed: Boolean,
)
