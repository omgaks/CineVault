package com.sole.cinevault

import android.view.WindowManager

/**
 * Returns Android's default window-brightness override value, preserving
 * the existing behavior when the external glasses display disconnects.
 */
internal fun playerDefaultWindowBrightness(): Float =
    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
