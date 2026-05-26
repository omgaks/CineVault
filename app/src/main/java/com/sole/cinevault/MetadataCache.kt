package com.sole.cinevault

import android.content.Context
import com.google.gson.Gson

private const val METADATA_PREFS_NAME = "cinevault_metadata_cache"

data class CachedVideoMetadata(
    val title: String,
    val subtitle: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val episodeStill: String?,
    val overview: String?,
    val rating: Double?,
    val imdbRating: String?,
    val rottenTomatoesRating: String?,
    val tmdbId: Int?,
    val type: String
)

fun loadCachedVideoMetadata(
    context: Context,
    videoPath: String
): CachedVideoMetadata? {
    val json =
        context
            .getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(videoPath, null)
            ?: return null

    return try {
        Gson().fromJson(json, CachedVideoMetadata::class.java)
    } catch (e: Exception) {
        null
    }
}

fun saveCachedVideoMetadata(
    context: Context,
    videoPath: String,
    item: VideoWithMetadata
) {
    val cached =
        CachedVideoMetadata(
            title = item.title,
            subtitle = item.subtitle,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            episodeStill = item.episodeStill,
            overview = item.overview,
            rating = item.rating,
            imdbRating = item.imdbRating,
            rottenTomatoesRating = item.rottenTomatoesRating,
            tmdbId = item.tmdbId,
            type = item.type
        )

    context
        .getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(videoPath, Gson().toJson(cached))
        .apply()
}

fun applyCachedVideoMetadata(
    item: VideoWithMetadata,
    cached: CachedVideoMetadata
): VideoWithMetadata {
    return item.copy(
        title = cached.title,
        subtitle = cached.subtitle,
        posterUrl = cached.posterUrl,
        backdropUrl = cached.backdropUrl,
        episodeStill = cached.episodeStill,
        overview = cached.overview,
        rating = cached.rating,
        imdbRating = cached.imdbRating,
        rottenTomatoesRating = cached.rottenTomatoesRating,
        tmdbId = cached.tmdbId,
        type = cached.type
    )
}

fun applyCachedMetadataIfAvailable(
    context: Context,
    item: VideoWithMetadata
): VideoWithMetadata {
    val cached = loadCachedVideoMetadata(context, item.video.path)
    return if (cached != null) {
        applyCachedVideoMetadata(item, cached)
    } else {
        item
    }
}

fun hasUsefulOnlineMetadata(item: VideoWithMetadata): Boolean {
    return !item.posterUrl.isNullOrBlank() ||
            !item.backdropUrl.isNullOrBlank() ||
            (item.tmdbId ?: 0) > 0
}

suspend fun enrichVideoWithOnlineMetadata(
    context: Context,
    item: VideoWithMetadata
): VideoWithMetadata {

    loadCachedVideoMetadata(context, item.video.path)?.let { cached ->
        return applyCachedVideoMetadata(item, cached)
    }

    val episodeInfo = extractEpisodeInfo(item.video.name)

    val enriched =
        if (episodeInfo != null) {

            val tv =
                try {
                    TmdbClient.api.searchTv(
                        bearerToken = BuildConfig.TMDB_TOKEN,
                        query = episodeInfo.showName
                    ).results.firstOrNull()
                } catch (e: Exception) {
                    null
                }

            val episodeDetails =
                try {
                    if (tv?.id != null) {
                        TmdbClient.api.getEpisodeDetails(
                            bearerToken = BuildConfig.TMDB_TOKEN,
                            seriesId = tv.id,
                            seasonNumber = episodeInfo.season,
                            episodeNumber = episodeInfo.episode
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

            item.copy(
                title = tv?.name ?: episodeInfo.showName,
                subtitle =
                    "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')} • ${episodeDetails?.name ?: ""}",
                posterUrl =
                    tv?.poster_path?.let {
                        "https://image.tmdb.org/t/p/w500$it"
                    },
                backdropUrl =
                    tv?.backdrop_path?.let {
                        "https://image.tmdb.org/t/p/original$it"
                    },
                episodeStill =
                    episodeDetails?.still_path?.let {
                        "https://image.tmdb.org/t/p/w780$it"
                    },
                overview = tv?.overview ?: item.overview,
                rating = tv?.vote_average ?: item.rating,
                tmdbId = tv?.id ?: item.tmdbId,
                type = "tv"
            )

        } else {

            val movieSearchName = cleanMovieFilename(item.video.name)

            if (looksLikePersonalOrCameraVideoForCache(item.video.name, movieSearchName)) {
                item.copy(
                    title = item.video.name.substringBeforeLast("."),
                    subtitle = "Personal video",
                    posterUrl = null,
                    backdropUrl = null,
                    type = "local"
                )
            } else {
                val movieResults =
                    try {
                        TmdbClient.api.searchMovie(
                            bearerToken = BuildConfig.TMDB_TOKEN,
                            query = movieSearchName
                        ).results
                    } catch (e: Exception) {
                        emptyList()
                    }

                val movie =
                    if (movieSearchName.contains("sassy girl", ignoreCase = true)) {
                        movieResults.firstOrNull {
                            it.release_date?.startsWith("2001") == true
                        } ?: movieResults.firstOrNull()
                    } else {
                        movieResults.firstOrNull()
                    }

                item.copy(
                    title = movie?.title ?: item.title,
                    subtitle = movie?.release_date?.take(4) ?: item.subtitle,
                    posterUrl =
                        movie?.poster_path?.let {
                            "https://image.tmdb.org/t/p/w500$it"
                        },
                    backdropUrl =
                        movie?.backdrop_path?.let {
                            "https://image.tmdb.org/t/p/original$it"
                        },
                    overview = movie?.overview ?: item.overview,
                    rating = movie?.vote_average ?: item.rating,
                    tmdbId = movie?.id ?: item.tmdbId,
                    type = "movie"
                )
            }
        }

    saveCachedVideoMetadata(context, item.video.path, enriched)
    return enriched
}

private fun looksLikePersonalOrCameraVideoForCache(
    fileName: String,
    cleanedName: String
): Boolean {
    val lower = fileName.lowercase()
    val cleaned = cleanedName.trim().lowercase()

    if (cleaned.length < 4) return true

    return lower.startsWith("vid_") ||
            lower.startsWith("img_") ||
            lower.startsWith("video_") ||
            lower.startsWith("screenrecord") ||
            lower.startsWith("screen_record") ||
            lower.contains("whatsapp video") ||
            lower.contains("camera") ||
            lower.matches(Regex(".*\\b(19|20)\\d{6}[_-]?(19|20)?\\d{0,6}.*"))
}
