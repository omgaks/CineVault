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

/**
 * Shares the report as a small text file. A long log pasted straight into the share
 * intent can be too big for Android to hand over, and the share sheet then never opens.
 */
internal fun shareCrashReport(context: Context, log: String) {
    val text = buildCrashReportText(
        appVersion = appVersionName(context),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        log = log
    )
    val ok = runCatching {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = java.io.File(dir, "cinevault-crash-report.txt")
        file.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.shareprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CineVault crash report")
            putExtra(Intent.EXTRA_TEXT, "CineVault crash report (${appVersionName(context)}, ${Build.MANUFACTURER} ${Build.MODEL}). The full log is attached.")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData("Crash report", arrayOf("text/plain"), android.content.ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) {
        android.widget.Toast.makeText(context, "Could not open the share sheet. Use Copy instead.", android.widget.Toast.LENGTH_LONG).show()
    }
}
