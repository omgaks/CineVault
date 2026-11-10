package com.sole.cinevault.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * When to show the "keep sharing alive with the screen off" tip on Share My Library.
 *
 * Only while sharing is on (that is when it matters), only if Android does not already
 * exempt CineVault from battery optimisation, and never again once the user dismissed it.
 */
internal fun shouldShowSharingBatteryTip(
    sharingRunning: Boolean,
    ignoringBatteryOptimizations: Boolean,
    dismissed: Boolean,
): Boolean = sharingRunning && !ignoringBatteryOptimizations && !dismissed

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    power?.isIgnoringBatteryOptimizations(context.packageName) == true
}.getOrDefault(false)

private const val PREFS = "cinevault_nearby"
private const val KEY_TIP_DISMISSED = "battery_tip_dismissed"

internal fun isSharingBatteryTipDismissed(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TIP_DISMISSED, false)

internal fun dismissSharingBatteryTip(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_TIP_DISMISSED, true).apply()
}

/**
 * Opens the system battery-optimisation list (needs no special permission, so it is safe
 * for Play Store policy). Falls back to CineVault's own app-info page, then does nothing.
 */
internal fun openBatterySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (intent in intents) {
        val started = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (started) return
    }
}
