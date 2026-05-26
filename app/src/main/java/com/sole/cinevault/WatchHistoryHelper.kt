package com.sole.cinevault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryEntry(
    val videoPath: String,
    val title: String,
    val watchedAt: Long
)

private const val WATCH_HISTORY_PREF = "cinevault_watch_history"
private const val WATCH_HISTORY_KEY = "items"

fun recordWatchHistory(
    context: Context,
    videoPath: String,
    title: String
) {
    if (videoPath.isBlank()) return

    val currentItems = loadWatchHistory(context).toMutableList()

    val updatedItems =
        listOf(
            WatchHistoryEntry(
                videoPath = videoPath,
                title = title.ifBlank { videoPath.substringAfterLast("/") },
                watchedAt = System.currentTimeMillis()
            )
        ) + currentItems.filterNot { it.videoPath == videoPath }

    saveWatchHistory(
        context = context,
        items = updatedItems.take(60)
    )
}

fun loadWatchHistory(context: Context): List<WatchHistoryEntry> {
    val prefs = context.getSharedPreferences(WATCH_HISTORY_PREF, Context.MODE_PRIVATE)
    val raw = prefs.getString(WATCH_HISTORY_KEY, "[]") ?: "[]"

    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val path = obj.optString("videoPath")
                if (path.isBlank()) continue

                add(
                    WatchHistoryEntry(
                        videoPath = path,
                        title = obj.optString("title"),
                        watchedAt = obj.optLong("watchedAt", 0L)
                    )
                )
            }
        }.sortedByDescending { it.watchedAt }
    } catch (_: Exception) {
        emptyList()
    }
}

fun loadWatchHistoryItems(
    context: Context,
    videos: List<VideoWithMetadata>
): List<VideoWithMetadata> {
    val videoMap = videos.associateBy { it.video.path }

    return loadWatchHistory(context)
        .mapNotNull { entry -> videoMap[entry.videoPath] }
}

private fun saveWatchHistory(
    context: Context,
    items: List<WatchHistoryEntry>
) {
    val array = JSONArray()

    items.forEach { item ->
        array.put(
            JSONObject().apply {
                put("videoPath", item.videoPath)
                put("title", item.title)
                put("watchedAt", item.watchedAt)
            }
        )
    }

    context
        .getSharedPreferences(WATCH_HISTORY_PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(WATCH_HISTORY_KEY, array.toString())
        .apply()
}
