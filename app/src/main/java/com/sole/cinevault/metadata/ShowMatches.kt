package com.sole.cinevault.metadata

import android.content.Context

/**
 * Show-level matching helpers.
 *
 * Files name a series one way ("Dawang Raoming") while TMDB lists it another ("Spare Me, Great
 * Lord!" / 大王饶命). Two small pieces close that gap without any new network service:
 *  - a few known alternative titles to try after the filename's own spelling fails, and
 *  - a memory of which TMDB show each filename-show was matched to, so every episode of a
 *    series reuses one match instead of searching again, and a manual Fix Match on one episode
 *    carries over to the rest.
 */
internal fun showKey(showName: String): String =
    showName.lowercase().filter { it.isLetterOrDigit() }

/** Alternative titles for series whose filename spelling is not what TMDB uses. Keys are showKey()s. */
private val knownShowAliases: Map<String, List<String>> = mapOf(
    "dawangraoming" to listOf("Spare Me, Great Lord!", "大王饶命"),
)

/** The filename's own spelling first, then any known alternatives, without duplicates. */
internal fun showSearchQueries(showName: String): List<String> =
    (listOf(showName.trim()) + (knownShowAliases[showKey(showName)] ?: emptyList()))
        .filter { it.isNotBlank() }
        .distinct()

private const val SHOW_MATCH_PREFS = "cinevault_show_matches"

object ShowMatchMemory {
    fun get(context: Context, showName: String): Int? {
        val key = showKey(showName)
        if (key.isEmpty()) return null
        val id = context.getSharedPreferences(SHOW_MATCH_PREFS, Context.MODE_PRIVATE).getInt(key, 0)
        return id.takeIf { it > 0 }
    }

    fun put(context: Context, showName: String, tmdbId: Int) {
        val key = showKey(showName)
        if (key.isEmpty() || tmdbId <= 0) return
        context.getSharedPreferences(SHOW_MATCH_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(key, tmdbId).apply()
    }
}

/**
 * Decides whether a bare-"E01" name is really a film: TMDB knows a movie with exactly this
 * title and no series with it. A series with the same name always wins, so real shows named
 * like a film are never pulled out of TV Shows.
 */
internal fun bareNameIsFilm(showName: String, tvTitles: List<String>, movieTitles: List<String>): Boolean {
    val key = showKey(showName)
    if (key.length < 4) return false
    val tvExact = tvTitles.any { showKey(it) == key }
    val movieExact = movieTitles.any { showKey(it) == key }
    return movieExact && !tvExact
}
