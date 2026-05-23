package com.sole.cinevault

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CachedLibrary(
    val videos: List<VideoWithMetadata>,
    val timestamp: Long
)

fun savePlaybackPosition(
    context: Context,
    videoPath: String,
    position: Long
) {
    context
        .getSharedPreferences("playback_memory", Context.MODE_PRIVATE)
        .edit()
        .putLong(videoPath, position)
        .apply()
}

fun loadPlaybackPosition(
    context: Context,
    videoPath: String
): Long {
    return context
        .getSharedPreferences("playback_memory", Context.MODE_PRIVATE)
        .getLong(videoPath, 0L)
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