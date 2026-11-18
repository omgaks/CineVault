package com.sole.cinevault.glasses.halo

/** Scroll stays a little livelier than the finger: a small pad drives a big glasses screen. */
internal const val PAD_SCROLL_GAIN = 1.6f

/** Holding one finger still this long turns a drag into a real drag (sliders, seek bars). */
internal const val PAD_HOLD_TO_DRAG_MS = 450L

/** A finger that stays within this travel (fraction of the pad) still counts as "held still". */
internal const val PAD_HOLD_SLOP_FRACTION = 0.012f

/** Waiting this long for a second finger before a lone finger becomes a pointer press. */
internal const val PAD_SECOND_FINGER_WAIT_MS = 70L

/** Where the synthetic scroll finger is, as a 0..1 position on the glasses screen. */
internal class HaloPadScrollState {
    private var x = 0.5f
    private var y = 0.5f

    fun begin(origin: HaloVector) {
        x = origin.x.coerceIn(0f, 1f)
        y = origin.y.coerceIn(0f, 1f)
    }

    fun move(dxFraction: Float, dyFraction: Float): HaloVector {
        x = (x + dxFraction * PAD_SCROLL_GAIN).coerceIn(0f, 1f)
        y = (y + dyFraction * PAD_SCROLL_GAIN).coerceIn(0f, 1f)
        return HaloVector(x, y)
    }

    fun last() = HaloVector(x, y)
}
