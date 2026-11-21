package com.sole.cinevault

import android.content.Context
import com.sole.cinevault.glasses.calibration.GlassesCalibrationIdentity

/**
 * Per-glasses-model calibration (Phase 7). Keyed by the external display's
 * reported name (Android's Display.getName(), surfaced already as
 * ExternalDisplayInfo.displayName) — the closest thing to a stable model
 * identifier available without a vendor SDK. Firmware updates or two units
 * of the same model reporting slightly different strings would each be
 * treated as a new "model" here; that's a known rough edge, not a bug —
 * there's no more reliable identifier to key on without one.
 */
private const val CALIBRATION_PREFS = "cinevault_glasses_calibration"

// Community-sourced dp/font corrections for models people have already
// confirmed look right, matched by a lowercase substring of the reported
// display name (exact strings vary by firmware). Starts empty — nothing
// has been confirmed by anyone yet, so every model goes through the
// manual calibration prompt until real reports grow this list. Ship
// updates to this map as confirmations come in; it's the "bundled
// known-good profiles" the roadmap describes.
private val BUNDLED_KNOWN_GOOD_FONT_SCALE: Map<String, Float> = emptyMap()

fun bundledFontScale(displayName: String?): Float? {
    val name = displayName?.lowercase() ?: return null
    return BUNDLED_KNOWN_GOOD_FONT_SCALE.entries.firstOrNull { name.contains(it.key) }?.value
}

// No reported name at all → skip calibration rather than nag forever with
// nothing to key a saved answer against.
fun needsCalibration(context: Context, displayName: String?): Boolean {
    if (!calibrationAskOnConnect(context)) return false
    val key = GlassesCalibrationIdentity.preferenceKey(displayName) ?: return false
    if (bundledFontScale(displayName) != null) return false
    return !context.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
        .contains(key)
}

fun markCalibrated(context: Context, displayName: String) {
    val key = GlassesCalibrationIdentity.preferenceKey(displayName) ?: return
    context.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(key, true)
        .apply()
}

private const val KEY_ASK_ON_CONNECT = "ask_on_connect"

/** Settings switch: show the calibration card when new glasses connect. On by default. */
fun calibrationAskOnConnect(context: Context): Boolean =
    context.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASK_ON_CONNECT, true)

fun setCalibrationAskOnConnect(context: Context, value: Boolean) {
    context.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ASK_ON_CONNECT, value).apply()
}
