package com.sole.cinevault

import android.content.Context
import org.json.JSONArray

// Scan sources let users EXCLUDE specific folders from the scan.
// By default (empty list) ALL folders are included.
// This is the opposite of the old behaviour which only included specific folders.

private const val SCAN_SOURCES_PREF = "cinevault_scan_sources"
private const val EXCLUDED_FOLDERS_KEY = "excluded_folders"

fun loadExcludedFolders(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SCAN_SOURCES_PREF, Context.MODE_PRIVATE)
    val raw = prefs.getString(EXCLUDED_FOLDERS_KEY, "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
    } catch (_: Exception) { emptySet() }
}

fun saveExcludedFolders(context: Context, folders: Set<String>) {
    val array = JSONArray()
    folders.forEach { array.put(it) }
    context.getSharedPreferences(SCAN_SOURCES_PREF, Context.MODE_PRIVATE)
        .edit().putString(EXCLUDED_FOLDERS_KEY, array.toString()).apply()
}

// FIX: Returns true (allowed) for everything UNLESS the path is in an excluded folder.
// Old behaviour returned false for most paths which broke scanning for most users.
fun isVideoAllowedByScanSources(context: Context, videoPath: String): Boolean {
    val excluded = loadExcludedFolders(context)
    if (excluded.isEmpty()) return true // no exclusions = allow everything
    return excluded.none { excludedFolder -> videoPath.startsWith(excludedFolder) }
}

// Legacy: kept for compatibility with SettingsScreen etc.
fun loadScanSources(context: Context): List<String> {
    return loadExcludedFolders(context).toList()
}

fun saveScanSources(context: Context, sources: List<String>) {
    saveExcludedFolders(context, sources.toSet())
}
