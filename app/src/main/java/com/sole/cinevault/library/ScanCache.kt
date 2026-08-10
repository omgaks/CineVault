package com.sole.cinevault.library

import android.content.Context

// FIX: Phase 5 of the SharedPreferences-as-database migration — see
// ScannedPathDatabase.kt for the full reasoning. Same one-time migration
// pattern as previous phases.
private const val SCAN_MIGRATION_DONE_KEY = "scan_cache_room_migration_done"
private var scanMigrationChecked = false

private fun ensureScanCacheMigratedToRoom(context: Context) {
    if (scanMigrationChecked) return
    scanMigrationChecked = true
    val settingsPrefs = context.getSharedPreferences("cinevault_scan_cache_settings", Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(SCAN_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences("cinevault_scan_cache", Context.MODE_PRIVATE)
    val legacyPaths = legacyPrefs.getStringSet("scanned_paths", null)
    if (!legacyPaths.isNullOrEmpty()) {
        ScannedPathDatabase.getInstance(context).scannedPathDao()
            .insertAll(legacyPaths.map { ScannedPathEntity(it) })
    }
    legacyPrefs.edit().clear().apply()
    settingsPrefs.edit().putBoolean(SCAN_MIGRATION_DONE_KEY, true).apply()
}

fun saveScannedVideoPaths(
    context: Context,
    paths: Set<String>
) {
    ensureScanCacheMigratedToRoom(context)
    val dao = ScannedPathDatabase.getInstance(context).scannedPathDao()
    dao.clear()
    dao.insertAll(paths.map { ScannedPathEntity(it) })
}

fun loadScannedVideoPaths(
    context: Context
): Set<String> {
    ensureScanCacheMigratedToRoom(context)
    return ScannedPathDatabase.getInstance(context).scannedPathDao().getAll().toSet()
}

fun clearScannedVideoCache(
    context: Context
) {
    ensureScanCacheMigratedToRoom(context)
    ScannedPathDatabase.getInstance(context).scannedPathDao().clear()
}
