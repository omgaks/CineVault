package com.sole.cinevault.metadata

import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata

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
            // FIX: a query like "wall E 2008" was being sent to TMDB
            // completely as-is — including the year as part of the free-
            // text search string, which TMDB's search doesn't handle
            // well (a year isn't part of a movie's actual title). The
            // automatic matching path already avoids this via
            // tmdbMovieSearchQuery() stripping the year before search;
            // this manual path just never had the same treatment.
            val yearHint = extractYearHint(query)
            val titleOnlyQuery = query.replace(Regex("\\b(19\\d{2}|20\\d{2})\\b"), "").replace(Regex("\\s+"), " ").trim()
            val finalQuery = titleOnlyQuery.ifBlank { query }

            val yearFiltered = TmdbClient.api
                .searchMovie(bearerToken = BuildConfig.TMDB_TOKEN, query = finalQuery, primaryReleaseYear = yearHint)
                .results

            // FIX: primary_release_year filters strictly against TMDB's
            // single registered "primary" release date for a film — which
            // can genuinely differ from the year most people know a film
            // by (a festival premiere registered as primary instead of
            // the wide release, for example). That mismatch would zero
            // out results for a search typed with the commonly-known
            // year, for a film that unambiguously exists on TMDB. Retried
            // without the year filter specifically when a year was
            // provided AND it returned nothing — a plain query with no
            // year hint at all was already unfiltered, nothing to fall
            // back from there.
            val results = if (yearFiltered.isEmpty() && yearHint != null) {
                TmdbClient.api
                    .searchMovie(bearerToken = BuildConfig.TMDB_TOKEN, query = finalQuery, primaryReleaseYear = null)
                    .results
            } else {
                yearFiltered
            }

            results
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

    val tmdbPoster = candidate.posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val tmdbBackdrop = candidate.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val fanart = if (tmdbPoster == null || tmdbBackdrop == null) {
        fetchFanartArtwork(candidate.tmdbId, "movie")
    } else {
        null
    }

    val updated = currentItem.copy(
        title = candidate.title,
        subtitle = candidate.releaseYear?.toString() ?: currentItem.subtitle,
        posterUrl = tmdbPoster ?: fanart?.posterUrl,
        backdropUrl = tmdbBackdrop ?: fanart?.backdropUrl,
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

/**
 * Refreshes artwork for the existing TMDB match without searching for or
 * changing the matched title. TMDB remains the first source; Fanart.tv is
 * consulted only for an image TMDB still does not provide.
 */
suspend fun refreshArtwork(
    context: Context,
    currentItem: VideoWithMetadata
): VideoWithMetadata = withContext(Dispatchers.IO) {
    val tmdbId = currentItem.tmdbId
        ?: throw IllegalStateException("Use Fix Match before refreshing artwork")

    val details = if (currentItem.type == "tv") {
        TmdbClient.api.getTvDetails(BuildConfig.TMDB_TOKEN, tmdbId)
    } else {
        TmdbClient.api.getMovieDetails(BuildConfig.TMDB_TOKEN, tmdbId)
    }

    val tmdbPosterPath = when (details) {
        is TmdbTvDetails -> details.poster_path
        is TmdbMovieDetails -> details.poster_path
        else -> null
    }
    val tmdbBackdropPath = when (details) {
        is TmdbTvDetails -> details.backdrop_path
        is TmdbMovieDetails -> details.backdrop_path
        else -> null
    }

    val tmdbPoster = tmdbPosterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val tmdbBackdrop = tmdbBackdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val fanart = if (tmdbPoster == null || tmdbBackdrop == null) {
        fetchFanartArtwork(tmdbId, currentItem.type)
    } else {
        null
    }

    val updated = currentItem.copy(
        posterUrl = tmdbPoster ?: fanart?.posterUrl ?: currentItem.posterUrl,
        backdropUrl = tmdbBackdrop ?: fanart?.backdropUrl ?: currentItem.backdropUrl
    )
    saveCachedVideoMetadata(context, currentItem.video.path, updated)
    updated
}

suspend fun refreshTvArtwork(
    context: Context,
    episodes: List<VideoWithMetadata>
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {
    val representative = episodes.firstOrNull { it.tmdbId != null }
        ?: throw IllegalStateException("No matched TV metadata is available")
    val refreshed = refreshArtwork(context, representative)
    val updatedEpisodes = episodes.map { episode ->
        episode.copy(
            posterUrl = refreshed.posterUrl,
            backdropUrl = refreshed.backdropUrl,
            tmdbId = refreshed.tmdbId,
            type = "tv"
        )
    }
    updatedEpisodes.forEach { episode ->
        saveCachedVideoMetadata(context, episode.video.path, episode)
    }
    updatedEpisodes
}
