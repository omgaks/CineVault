package com.sole.cinevault.glasses.halo

/**
 * D11-S1 — bridges the tablet Cinema Void touch surface to the canonical Halo
 * surface rendered on the external display.
 *
 * Source and target geometry are always supplied from the live Compose
 * surfaces. No device model, fixed resolution or orientation constant is used.
 */
internal class HaloRemoteInputRouter {
    private var targetViewport: HaloViewport? = null

    fun updateTargetViewport(widthPx: Int, heightPx: Int) {
        targetViewport =
            if (widthPx > 0 && heightPx > 0) {
                HaloViewport(widthPx = widthPx, heightPx = heightPx)
            } else {
                null
            }
    }

    fun map(
        sample: HaloPointerSample,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
    ): HaloPointerSample? {
        val target = targetViewport ?: return null
        if (sourceWidthPx <= 0 || sourceHeightPx <= 0) return null

        val point =
            HaloOrientationMapper.map(
                sample = sample,
                sourceWidthPx = sourceWidthPx,
                sourceHeightPx = sourceHeightPx,
                targetViewport = target,
            )

        return HaloPointerSample(
            xPx = point.xPx,
            yPx = point.yPx,
            xFraction = point.xFraction,
            yFraction = point.yFraction,
            pressed = point.pressed,
        )
    }

    fun reset() {
        targetViewport = null
    }
}

internal object HaloRemoteInputSession {
    private val router = HaloRemoteInputRouter()
    private var receiver: ((HaloPointerSample) -> Unit)? = null

    fun updateTargetViewport(widthPx: Int, heightPx: Int) {
        router.updateTargetViewport(widthPx, heightPx)
    }

    fun setReceiver(value: ((HaloPointerSample) -> Unit)?) {
        receiver = value
    }

    fun publish(
        sample: HaloPointerSample,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
    ) {
        router.map(
            sample = sample,
            sourceWidthPx = sourceWidthPx,
            sourceHeightPx = sourceHeightPx,
        )?.let { mapped ->
            receiver?.invoke(mapped)
        }
    }

    fun clearTargetViewport() {
        router.reset()
    }
}
