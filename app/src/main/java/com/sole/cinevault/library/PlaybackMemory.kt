package com.sole.cinevault.library

import com.sole.cinevault.VideoWithMetadata

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CachedLibrary(
    val videos: List<VideoWithMetadata>,
    val timestamp: Long
)

// FIX: Phase 3 of the SharedPreferences-as-database migration — see
// PlaybackPositionDatabase.kt for the full reasoning. Same one-time
// migration pattern as Phases 1 and 2: guarded by a persisted flag so the
// legacy prefs file only ever gets scanned once.
private const val PLAYBACK_MIGRATION_DONE_KEY = "playback_room_migration_done"
private var playbackMigrationChecked = false

private fun ensurePlaybackMigratedToRoom(context: Context) {
    if (playbackMigrationChecked) return
    playbackMigrationChecked = true
    val settingsPrefs = context.getSharedPreferences("playback_memory_settings", Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(PLAYBACK_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences("playback_memory", Context.MODE_PRIVATE)
    val legacyEntries = legacyPrefs.all
    if (legacyEntries.isNotEmpty()) {
        val dao = PlaybackPositionDatabase.getInstance(context).playbackPositionDao()
        val migrated = legacyEntries.mapNotNull { (videoPath, rawValue) ->
            (rawValue as? Long)?.takeIf { it > 0L }?.let { PlaybackPositionEntity(videoPath, it) }
        }
        if (migrated.isNotEmpty()) dao.upsertAll(migrated)
        legacyPrefs.edit().clear().apply()
    }
    settingsPrefs.edit().putBoolean(PLAYBACK_MIGRATION_DONE_KEY, true).apply()
}

fun savePlaybackPosition(
    context: Context,
    videoPath: String,
    position: Long
) {
    ensurePlaybackMigratedToRoom(context)
    PlaybackPositionDatabase.getInstance(context).playbackPositionDao()
        .upsert(PlaybackPositionEntity(videoPath, position))
}

fun loadPlaybackPosition(
    context: Context,
    videoPath: String
): Long {
    ensurePlaybackMigratedToRoom(context)
    return PlaybackPositionDatabase.getInstance(context).playbackPositionDao().getPosition(videoPath) ?: 0L
}

fun clearPlaybackPosition(
    context: Context,
    videoPath: String
) {
    ensurePlaybackMigratedToRoom(context)
    PlaybackPositionDatabase.getInstance(context).playbackPositionDao().delete(videoPath)
}

fun clearPlaybackFolderPositions(
    context: Context,
    folderPath: String
) {
    ensurePlaybackMigratedToRoom(context)
    val dao = PlaybackPositionDatabase.getInstance(context).playbackPositionDao()
    dao.getAllPaths()
        .filter { it.startsWith(folderPath) }
        .forEach { dao.delete(it) }
}

fun saveLibraryCache(
    context: Context,
    videos: List<VideoWithMetadata>
) {
    val cache =
        CachedLibrary(
            videos = videos,
            timestamp = System.currentTimeMillis()
        )

    val json =
        Gson().toJson(cache)

    context
        .getSharedPreferences("library_cache", Context.MODE_PRIVATE)
        .edit()
        .putString("cached_library", json)
        .apply()
}

fun loadLibraryCache(
    context: Context
): CachedLibrary? {
    val json =
        context
            .getSharedPreferences("library_cache", Context.MODE_PRIVATE)
            .getString("cached_library", null)
            ?: return null

    return try {
        val cacheType =
            object : TypeToken<CachedLibrary>() {}.type

        Gson().fromJson(json, cacheType)
    } catch (e: Exception) {
        null
    }
}

fun clearLibraryCache(context: Context) {
    context
        .getSharedPreferences("library_cache", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}
