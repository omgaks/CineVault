package com.sole.cinevault.library

import android.content.Context

// FIX: Phase 6 of the SharedPreferences-as-database migration — see
// FavoritePathDatabase.kt for the full reasoning. Same one-time migration
// pattern as previous phases.
private const val FAVORITE_MIGRATION_DONE_KEY = "favorites_room_migration_done"
private var favoriteMigrationChecked = false

private fun ensureFavoritesMigratedToRoom(context: Context) {
    if (favoriteMigrationChecked) return
    favoriteMigrationChecked = true
    val settingsPrefs = context.getSharedPreferences("cinevault_favorites_settings", Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(FAVORITE_MIGRATION_DONE_KEY, false)) return

    val legacyPrefs = context.getSharedPreferences("cinevault_favorites", Context.MODE_PRIVATE)
    val legacyPaths = legacyPrefs.getStringSet("favorite_video_paths", null)
    if (!legacyPaths.isNullOrEmpty()) {
        FavoritePathDatabase.getInstance(context).favoritePathDao()
            .insertAll(legacyPaths.map { FavoritePathEntity(it) })
    }
    legacyPrefs.edit().clear().apply()
    settingsPrefs.edit().putBoolean(FAVORITE_MIGRATION_DONE_KEY, true).apply()
}

fun loadFavoriteVideoPaths(context: Context): Set<String> {
    ensureFavoritesMigratedToRoom(context)
    return FavoritePathDatabase.getInstance(context).favoritePathDao().getAll().toSet()
}

fun saveFavoriteVideoPaths(
    context: Context,
    paths: Set<String>
) {
    ensureFavoritesMigratedToRoom(context)
    val dao = FavoritePathDatabase.getInstance(context).favoritePathDao()
    dao.clear()
    dao.insertAll(paths.map { FavoritePathEntity(it) })
}
