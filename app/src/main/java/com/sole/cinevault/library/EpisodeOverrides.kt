package com.sole.cinevault.library

import android.content.Context

/**
 * Remembers decisions about files whose names look like "Something E01" but could be either a
 * film or a series episode:
 *  - [isMovie]: this file is a film (decided from TMDB, or by the person via Fix Match).
 *  - [isConfirmedEpisode]: this file really is an episode, so never re-check it.
 *
 * Without this memory the "looks like an episode" name rule would keep flipping a film back
 * into TV Shows on every load.
 */
object EpisodeOverrides {
    private const val PREFS = "cinevault_episode_overrides"
    private const val KEY_MOVIES = "movie_paths"
    private const val KEY_EPISODES = "episode_paths"

    @Volatile private var movies: Set<String>? = null
    @Volatile private var episodes: Set<String>? = null

    fun ensureLoaded(context: Context) {
        if (movies != null && episodes != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        movies = prefs.getStringSet(KEY_MOVIES, emptySet()).orEmpty().toSet()
        episodes = prefs.getStringSet(KEY_EPISODES, emptySet()).orEmpty().toSet()
    }

    fun isMovie(path: String): Boolean = movies?.contains(path) == true

    fun isConfirmedEpisode(path: String): Boolean = episodes?.contains(path) == true

    @Synchronized
    fun markMovie(context: Context, path: String) {
        ensureLoaded(context)
        movies = movies.orEmpty() + path
        episodes = episodes.orEmpty() - path
        save(context)
    }

    @Synchronized
    fun markEpisode(context: Context, path: String) {
        ensureLoaded(context)
        episodes = episodes.orEmpty() + path
        movies = movies.orEmpty() - path
        save(context)
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_MOVIES, movies.orEmpty())
            .putStringSet(KEY_EPISODES, episodes.orEmpty())
            .apply()
    }
}
