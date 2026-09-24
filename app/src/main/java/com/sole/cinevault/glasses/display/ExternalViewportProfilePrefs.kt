package com.sole.cinevault.glasses.display

import android.content.Context
import com.sole.cinevault.glasses.calibration.GlassesCalibrationIdentity

internal object ExternalViewportProfilePrefs {
    private const val PREFS = "cinevault_glasses_calibration"
    private const val SCALE = "viewport_scale_"
    private const val PAN_X = "viewport_pan_x_"
    private const val PAN_Y = "viewport_pan_y_"

    fun load(context: Context, displayName: String?): ExternalViewportTransform? {
        val identity =
            GlassesCalibrationIdentity.normalizedDisplayName(displayName)
                ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scaleKey = SCALE + identity
        if (!prefs.contains(scaleKey)) return null

        return ExternalViewportTransform(
            scale = prefs.getFloat(scaleKey, 1f),
            panX = prefs.getFloat(PAN_X + identity, 0f),
            panY = prefs.getFloat(PAN_Y + identity, 0f),
        )
    }

    fun save(
        context: Context,
        displayName: String?,
        transform: ExternalViewportTransform,
    ) {
        val identity =
            GlassesCalibrationIdentity.normalizedDisplayName(displayName)
                ?: return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(SCALE + identity, transform.scale)
            .putFloat(PAN_X + identity, transform.panX)
            .putFloat(PAN_Y + identity, transform.panY)
            .apply()
    }
}
