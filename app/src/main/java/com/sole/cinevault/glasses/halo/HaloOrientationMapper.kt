package com.sole.cinevault.glasses.halo

/**
 * D2-3 — orientation-aware Halo mapping.
 *
 * The touch surface and rendered CineVault display can independently be
 * portrait or landscape. We rotate the normalised input into the target
 * orientation BEFORE D2-2 projects it into target pixels.
 *
 * No device name, fixed resolution or Android Configuration constant is baked
 * into the mapping.
 */
object HaloOrientationMapper {

    fun map(
        sample: HaloPointerSample,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        targetViewport: HaloViewport,
    ): HaloDisplayPoint {
        require(sourceWidthPx > 0) { "Halo source width must be greater than zero." }
        require(sourceHeightPx > 0) { "Halo source height must be greater than zero." }

        val source = HaloOrientation.fromSize(sourceWidthPx, sourceHeightPx)
        val target = HaloOrientation.fromSize(
            targetViewport.widthPx,
            targetViewport.heightPx,
        )

        val oriented = when {
            source == target -> sample

            source == HaloOrientation.PORTRAIT &&
                target == HaloOrientation.LANDSCAPE ->
                sample.copy(
                    xFraction = sample.yFraction,
                    yFraction = 1f - sample.xFraction,
                )

            source == HaloOrientation.LANDSCAPE &&
                target == HaloOrientation.PORTRAIT ->
                sample.copy(
                    xFraction = 1f - sample.yFraction,
                    yFraction = sample.xFraction,
                )

            else -> sample
        }

        return HaloCoordinateEngine.map(
            sample = oriented,
            viewport = targetViewport,
        )
    }
}

enum class HaloOrientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun fromSize(widthPx: Int, heightPx: Int): HaloOrientation {
            require(widthPx > 0 && heightPx > 0) {
                "Halo orientation requires positive dimensions."
            }

            // Square/freeform surfaces keep LANDSCAPE semantics rather than
            // oscillating around a 1:1 resize boundary.
            return if (heightPx > widthPx) PORTRAIT else LANDSCAPE
        }
    }
}
