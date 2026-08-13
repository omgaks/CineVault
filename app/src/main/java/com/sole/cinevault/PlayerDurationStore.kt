package com.sole.cinevault

import android.content.Context

private const val DURATIONS_MIGRATION_DONE_KEY = "durations_room_migration_done"

private var durationsMigrationChecked = false

/**
 * Persists the real Media3 duration used by library progress indicators.
 * Also owns the one-time migration from the former SharedPreferences store.
 */
private fun ensureDurationsMigratedToRoom(context: Context) {
    if (durationsMigrationChecked) return
    durationsMigrationChecked = true

    val settingsPrefs = context.getSharedPreferences(
        "cinevault_durations_settings",
        Context.MODE_PRIVATE
    )
    if (settingsPrefs.getBoolean(DURATIONS_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences(
        "cinevault_durations",
        Context.MODE_PRIVATE
    )
    val legacyEntries = legacyPrefs.all
    if (legacyEntries.isNotEmpty()) {
        val dao = VideoDurationDatabase.getInstance(context).videoDurationDao()
        val migrated = legacyEntries.mapNotNull { (videoPath, rawValue) ->
            (rawValue as? Long)
                ?.takeIf { it > 0L }
                ?.let { VideoDurationEntity(videoPath, it) }
        }
        if (migrated.isNotEmpty()) dao.upsertAll(migrated)
        legacyPrefs.edit().clear().apply()
    }

    settingsPrefs.edit()
        .putBoolean(DURATIONS_MIGRATION_DONE_KEY, true)
        .apply()
}

internal fun savePlayerDuration(
    context: Context,
    videoPath: String,
    durationMs: Long
) {
    if (durationMs <= 0L) return
    ensureDurationsMigratedToRoom(context)
    VideoDurationDatabase.getInstance(context)
        .videoDurationDao()
        .upsert(VideoDurationEntity(videoPath, durationMs))
}

fun loadDuration(context: Context, videoPath: String): Long {
    ensureDurationsMigratedToRoom(context)
    return VideoDurationDatabase.getInstance(context)
        .videoDurationDao()
        .getDuration(videoPath) ?: 0L
}
