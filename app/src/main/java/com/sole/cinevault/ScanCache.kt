package com.sole.cinevault

import android.content.Context

fun saveScannedVideoPaths(
    context: Context,
    paths: Set<String>
) {
    context
        .getSharedPreferences("cinevault_scan_cache", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("scanned_paths", paths)
        .apply()
}

fun loadScannedVideoPaths(
    context: Context
): Set<String> {
    return context
        .getSharedPreferences("cinevault_scan_cache", Context.MODE_PRIVATE)
        .getStringSet("scanned_paths", emptySet())
        ?: emptySet()
}

fun clearScannedVideoCache(
    context: Context
) {
    context
        .getSharedPreferences("cinevault_scan_cache", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}