package com.sole.cinevault.glasses

import android.content.Context
import com.sole.cinevault.CRASH_LOG_FILE_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small breadcrumb trail for glasses mode. Lines go into the same in-app log that Settings
 * shows ("View Crash Log"), so a glasses problem can be diagnosed without Logcat.
 * Never throws and never blocks playback.
 */
internal object GlassesTrace {
    private const val MAX_BYTES = 50_000
    private val lock = Any()

    fun log(context: Context, message: String) {
        runCatching {
            synchronized(lock) {
                val file = File(context.applicationContext.filesDir, CRASH_LOG_FILE_NAME)
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val existing = if (file.exists()) file.readText() else ""
                val trimmed = if (existing.length > MAX_BYTES) existing.takeLast(MAX_BYTES) else existing
                file.writeText(trimmed + "\n[glasses $stamp] $message")
            }
        }
    }
}
