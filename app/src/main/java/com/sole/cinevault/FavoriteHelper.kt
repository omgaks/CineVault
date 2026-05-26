package com.sole.cinevault

import android.content.Context

private const val FAVORITE_PREFS_NAME = "cinevault_favorites"
private const val FAVORITE_PATHS_KEY = "favorite_video_paths"

fun loadFavoriteVideoPaths(context: Context): Set<String> {
    return context
        .getSharedPreferences(FAVORITE_PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(FAVORITE_PATHS_KEY, emptySet<String>())
        ?.toSet()
        ?: emptySet()
}

fun saveFavoriteVideoPaths(
    context: Context,
    paths: Set<String>
) {
    context
        .getSharedPreferences(FAVORITE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(FAVORITE_PATHS_KEY, paths)
        .apply()
}
