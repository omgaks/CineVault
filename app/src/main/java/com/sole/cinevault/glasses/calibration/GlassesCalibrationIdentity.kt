package com.sole.cinevault.glasses.calibration

internal object GlassesCalibrationIdentity {
    fun normalizedDisplayName(displayName: String?): String? =
        displayName?.trim()?.takeIf { it.isNotEmpty() }

    fun preferenceKey(displayName: String?): String? =
        normalizedDisplayName(displayName)?.let { "calibrated_$it" }
}
