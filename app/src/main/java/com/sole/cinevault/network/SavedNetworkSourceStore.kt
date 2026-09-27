package com.sole.cinevault.network

import android.content.Context
import org.json.JSONArray

class SavedNetworkSourceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cinevault_network_sources",
        Context.MODE_PRIVATE,
    )

    fun list(): List<SavedNetworkSource> {
        val raw = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    SavedNetworkSource.fromJson(array.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(source: SavedNetworkSource) {
        val updated = list().filterNot { it.id == source.id } + source
        write(updated.sortedBy { it.displayName.lowercase() })
    }

    fun remove(sourceId: String) {
        if (!isSafeNetworkSourceId(sourceId)) return
        write(list().filterNot { it.id == sourceId })
    }

    private fun write(sources: List<SavedNetworkSource>) {
        val array = JSONArray()
        sources.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SOURCES, array.toString()).apply()
    }

    private companion object {
        const val KEY_SOURCES = "sources"
    }
}
