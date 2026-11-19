package com.sole.cinevault.glasses.halo

/** Scroll stays a little livelier than the finger: a small pad drives a big glasses screen. */
internal const val PAD_SCROLL_GAIN = 1.6f

/** Holding one finger still this long turns a drag into a real drag (sliders, seek bars). */
internal const val PAD_HOLD_TO_DRAG_MS = 450L

/** A finger that stays within this travel (fraction of the pad) still counts as "held still". */
internal const val PAD_HOLD_SLOP_FRACTION = 0.012f

/** Waiting this long for a second finger before a lone finger becomes a pointer press. */
internal const val PAD_SECOND_FINGER_WAIT_MS = 70L

/** Two fingers that land and lift within this time, barely moving, are a two-finger TAP (Back). */
internal const val PAD_TWO_FINGER_TAP_MS = 280L

/** Android's mouse wheel moves a list by this many dp per wheel unit. */
private const val DP_PER_WHEEL_UNIT = 64f

/**
 * Turns finger travel (a fraction of the pad) into wheel units for a glasses screen that is
 * [extentDp] dp long on that axis. Sign is not changed here.
 */
internal fun wheelUnits(fractionDelta: Float, extentDp: Float): Float =
    fractionDelta * extentDp * PAD_SCROLL_GAIN / DP_PER_WHEEL_UNIT
