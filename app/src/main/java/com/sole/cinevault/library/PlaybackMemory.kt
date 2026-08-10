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

// FIX: the fuller restructuring for library_cache — see
// LibraryCacheDatabase.kt for the full reasoning. Genuinely suspend this
// time (unlike Phases 2/3/4's synchronous approach), matching the real,
// previously-documented main-thread-hitch risk this specific store
// carries. Same one-time migration pattern as every other phase.
private const val LIBRARY_CACHE_MIGRATION_DONE_KEY = "library_cache_room_migration_done"
private var libraryCacheMigrationChecked = false

private suspend fun ensureLibraryCacheMigratedToRoom(context: Context) {
    if (libraryCacheMigrationChecked) return
    libraryCacheMigrationChecked = true
    val settingsPrefs = context.getSharedPreferences("library_cache_settings", Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(LIBRARY_CACHE_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences("library_cache", Context.MODE_PRIVATE)
    val raw = legacyPrefs.getString("cached_library", null)
    if (!raw.isNullOrBlank()) {
        try {
            val cacheType = object : TypeToken<CachedLibrary>() {}.type
            val legacy: CachedLibrary? = Gson().fromJson(raw, cacheType)
            if (legacy != null && legacy.videos.isNotEmpty()) {
                val dao = LibraryCacheDatabase.getInstance(context).libraryCacheDao()
                val entities = legacy.videos.map { LibraryCacheVideoEntity(it.video.path, Gson().toJson(it)) }
                dao.replaceLibraryCache(entities, legacy.timestamp)
            }
        } catch (_: Exception) {
            // Skip a corrupted legacy blob rather than fail startup over it.
        }
    }
    legacyPrefs.edit().clear().apply()
    settingsPrefs.edit().putBoolean(LIBRARY_CACHE_MIGRATION_DONE_KEY, true).apply()
}

suspend fun saveLibraryCache(
    context: Context,
    videos: List<VideoWithMetadata>
) {
    ensureLibraryCacheMigratedToRoom(context)
    val dao = LibraryCacheDatabase.getInstance(context).libraryCacheDao()
    val entities = videos.map { LibraryCacheVideoEntity(it.video.path, Gson().toJson(it)) }
    dao.replaceLibraryCache(entities, System.currentTimeMillis())
}

suspend fun loadLibraryCache(
    context: Context
): CachedLibrary? {
    ensureLibraryCacheMigratedToRoom(context)
    val dao = LibraryCacheDatabase.getInstance(context).libraryCacheDao()
    val meta = dao.getMeta() ?: return null
    val videoJsonList = dao.getAllVideoJson()
    if (videoJsonList.isEmpty()) return null
    val videos = videoJsonList.mapNotNull { json ->
        try {
            Gson().fromJson(json, VideoWithMetadata::class.java)
        } catch (_: Exception) {
            // Skip a corrupted individual video entry rather than fail
            // the whole cache load over one bad row.
            null
        }
    }
    return CachedLibrary(videos = videos, timestamp = meta.timestamp)
}

suspend fun clearLibraryCache(context: Context) {
    ensureLibraryCacheMigratedToRoom(context)
    LibraryCacheDatabase.getInstance(context).libraryCacheDao().clearLibraryCache()
}
