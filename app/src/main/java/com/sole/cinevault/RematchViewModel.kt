package com.sole.cinevault

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * RematchViewModel.kt
 *
 * Not an androidx ViewModel — plain suspend functions, same pattern as
 * enrichVideoWithOnlineMetadata in MetadataCache.kt. Called directly from
 * RematchDialog.kt via rememberCoroutineScope, no factory/DI needed.
 *
 * Reuses fetchTmdbExtraDetails / fetchOmdbRatings / extractTopCast from
 * MetadataCache.kt (now non-private) instead of duplicating that logic, so
 * this stays in sync automatically if that enrichment logic ever changes.
 */

data class MatchCandidate(
    val tmdbId: Int,
    val title: String,
    val releaseYear: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val voteAverage: Double?
)

/** Searches TMDB movies for the given query and returns candidate matches. */
suspend fun searchMovieCandidates(query: String): List<MatchCandidate> =
    withContext(Dispatchers.IO) {
        try {
            TmdbClient.api
                .searchMovie(
                    bearerToken = BuildConfig.TMDB_TOKEN,
                    query = query
                )
                .results
                .mapNotNull { movie ->
                    val id = movie.id ?: return@mapNotNull null
                    MatchCandidate(
                        tmdbId = id,
                        title = movie.title ?: "Untitled",
                        releaseYear = movie.release_date?.take(4)?.toIntOrNull(),
                        posterPath = movie.poster_path,
                        backdropPath = movie.backdrop_path,
                        overview = movie.overview,
                        voteAverage = movie.vote_average
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

/**
 * Applies a chosen TMDB match to an existing video's metadata: re-fetches
 * full details (genres, director, collection, cast) and OMDB ratings, then
 * overwrites the cached metadata for that video path, exactly like a fresh
 * enrichment would — just using the caller-chosen tmdbId instead of the
 * first automatic search hit.
 */
suspend fun applyRematch(
    context: Context,
    currentItem: VideoWithMetadata,
    candidate: MatchCandidate
): VideoWithMetadata {
    val extra = fetchTmdbExtraDetails(candidate.tmdbId, "movie")

    val (imdb, rt) = fetchOmdbRatings(
        candidate.title,
        candidate.releaseYear?.toString()
    )

    val updated = currentItem.copy(
        title = candidate.title,
        subtitle = candidate.releaseYear?.toString() ?: currentItem.subtitle,
        posterUrl = candidate.posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
            ?: currentItem.posterUrl,
        backdropUrl = candidate.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
            ?: currentItem.backdropUrl,
        overview = candidate.overview ?: currentItem.overview,
        rating = candidate.voteAverage ?: currentItem.rating,
        imdbRating = imdb ?: currentItem.imdbRating,
        rottenTomatoesRating = rt ?: currentItem.rottenTomatoesRating,
        tmdbId = candidate.tmdbId,
        type = "movie",
        genres = extra?.genres ?: emptyList(),
        director = extra?.director,
        collectionId = extra?.collectionId,
        collectionName = extra?.collectionName,
        curatedCollections = extra?.curatedCollections ?: emptyList(),
        cast = extra?.cast ?: emptyList()
    )

    saveCachedVideoMetadata(context, currentItem.video.path, updated)
    return updated
}
