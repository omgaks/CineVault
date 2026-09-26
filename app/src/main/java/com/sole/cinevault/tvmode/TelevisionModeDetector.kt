package com.sole.cinevault.tvmode

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Runtime detection of "is this a television device" — Android TV, Google TV,
 * or a Fire TV box (which also reports UI_MODE_TYPE_TELEVISION).
 *
 * Deliberately NOT based on screen size or touchscreen presence: a tablet in
 * landscape with a broken digitizer isn't a TV, and a TV box with a touch
 * overlay still is one. UI_MODE_TYPE is the one signal Android itself
 * commits to for this distinction, so it's the primary check here.
 *
 * The FEATURE_LEANBACK / android.hardware.type.television fallback exists
 * because a handful of older Fire TV builds have been known to misreport
 * uiMode — this is the same secondary signal the Play Store itself uses to
 * route a device to the TV listing of an app that ships both form factors.
 */
object TelevisionModeDetector {

    fun isRunningOnTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val uiModeSaysTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val pm = context.packageManager
        val featureSaysTv =
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television")

        return uiModeSaysTv || featureSaysTv
    }
}
