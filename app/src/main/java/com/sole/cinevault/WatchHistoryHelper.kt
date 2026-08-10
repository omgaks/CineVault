package com.sole.cinevault

import android.content.Context
import org.json.JSONArray

data class WatchHistoryEntry(
    val videoPath: String,
    val title: String,
    val watchedAt: Long
)

// FIX: Phase 7 of the SharedPreferences-as-database migration — see
// WatchHistoryDatabase.kt for the full reasoning. WatchHistoryEntry (the
// public-facing type every call site already uses) is kept completely
// unchanged — WatchHistoryEntity is purely the internal Room storage
// shape, converted at the boundary, so nothing calling into this file
// needs to change at all.
private const val WATCH_HISTORY_MIGRATION_DONE_KEY = "watch_history_room_migration_done"
private var watchHistoryMigrationChecked = false

private fun ensureWatchHistoryMigratedToRoom(context: Context) {
    if (watchHistoryMigrationChecked) return
    watchHistoryMigrationChecked = true
    val settingsPrefs = context.getSharedPreferences("cinevault_watch_history_settings", Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(WATCH_HISTORY_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences("cinevault_watch_history", Context.MODE_PRIVATE)
    val raw = legacyPrefs.getString("items", null)
    if (!raw.isNullOrBlank()) {
        try {
            val array = JSONArray(raw)
            val migrated = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val path = obj.optString("videoPath")
                    if (path.isBlank()) continue
                    add(WatchHistoryEntity(videoPath = path, title = obj.optString("title"), watchedAt = obj.optLong("watchedAt", 0L)))
                }
            }
            if (migrated.isNotEmpty()) {
                WatchHistoryDatabase.getInstance(context).watchHistoryDao().upsertAll(migrated)
            }
        } catch (_: Exception) {
            // Skip a corrupted legacy blob rather than fail startup over it.
        }
    }
    legacyPrefs.edit().clear().apply()
    settingsPrefs.edit().putBoolean(WATCH_HISTORY_MIGRATION_DONE_KEY, true).apply()
}

fun recordWatchHistory(
    context: Context,
    videoPath: String,
    title: String
) {
    if (videoPath.isBlank()) return
    ensureWatchHistoryMigratedToRoom(context)
    val dao = WatchHistoryDatabase.getInstance(context).watchHistoryDao()
    dao.upsert(
        WatchHistoryEntity(
            videoPath = videoPath,
            title = title.ifBlank { videoPath.substringAfterLast("/") },
            watchedAt = System.currentTimeMillis()
        )
    )
    dao.trimToMostRecent60()
}

fun loadWatchHistory(context: Context): List<WatchHistoryEntry> {
    ensureWatchHistoryMigratedToRoom(context)
    return WatchHistoryDatabase.getInstance(context).watchHistoryDao().getAll()
        .map { WatchHistoryEntry(videoPath = it.videoPath, title = it.title, watchedAt = it.watchedAt) }
}

fun loadWatchHistoryItems(
    context: Context,
    videos: List<VideoWithMetadata>
): List<VideoWithMetadata> {
    val videoMap = videos.associateBy { it.video.path }
    return loadWatchHistory(context).mapNotNull { entry -> videoMap[entry.videoPath] }
}
