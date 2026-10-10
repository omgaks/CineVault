package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Builds the text a person can share when something crashed. Manual share
 * only: nothing is ever uploaded by the app on its own.
 */
internal fun buildCrashReportText(
    appVersion: String,
    deviceName: String,
    androidVersion: String,
    log: String
): String {
    val body = log.ifBlank { "No crashes logged yet." }
    return buildString {
        appendLine("CineVault crash report")
        appendLine("App version: $appVersion")
        appendLine("Device: $deviceName")
        appendLine("Android: $androidVersion")
        appendLine()
        append(body)
    }
}

internal fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
} catch (_: Exception) {
    "unknown"
}

internal fun shareCrashReport(context: Context, log: String) {
    val text = buildCrashReportText(
        appVersion = appVersionName(context),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        log = log
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "CineVault crash report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
