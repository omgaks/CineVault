package com.sole.cinevault

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

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

/** True when a movie/TV item was matched online but never got IMDb/RT ratings. */
fun needsRatingsUpgrade(item: VideoWithMetadata): Boolean {
    return (item.type == "movie" || item.type == "tv") &&
            (item.tmdbId ?: 0) > 0 &&
            item.imdbRating.isNullOrBlank() &&
            item.rottenTomatoesRating.isNullOrBlank()
}

// ── OMDB — the source of IMDb and Rotten Tomatoes ratings ─────────────────────
// THE FIX: this was never called before. TMDB provides posters/cast/its own
// score, but IMDb and RT ratings only come from OMDB.

private val omdbHttpClient by lazy { OkHttpClient() }

private suspend fun fetchOmdbRatings(title: String, year: String?): Pair<String?, String?> =
    withContext(Dispatchers.IO) {
        try {
            val key = BuildConfig.OMDB_API_KEY
            if (key.isBlank()) return@withContext null to null
            val url = buildString {
                append("https://www.omdbapi.com/?apikey=").append(key)
                append("&t=").append(URLEncoder.encode(title, "UTF-8"))
                if (!year.isNullOrBlank() && year.length == 4) append("&y=").append(year)
            }
            omdbHttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null to null
                val json = JsonParser.parseString(body).asJsonObject
                if (json.get("Response")?.asString != "True") return@withContext null to null
                val imdb = json.get("imdbRating")?.asString?.takeIf { it.isNotBlank() && it != "N/A" }
                var rt: String? = null
                if (json.has("Ratings") && json.get("Ratings").isJsonArray) {
                    json.getAsJsonArray("Ratings").forEach { el ->
                        val o = el.asJsonObject
                        if (o.get("Source")?.asString == "Rotten Tomatoes") {
                            rt = o.get("Value")?.asString?.takeIf { it.isNotBlank() && it != "N/A" }
                        }
                    }
                }
                imdb to rt
            }
        } catch (e: Exception) {
            null to null
        }
    }

suspend fun enrichVideoWithOnlineMetadata(
    context: Context,
    item: VideoWithMetadata
): VideoWithMetadata {

    loadCachedVideoMetadata(context, item.video.path)?.let { cached ->
        val applied = applyCachedVideoMetadata(item, cached)
        // RATINGS UPGRADE: cached before the OMDB fix? Fetch just the ratings now.
        if (!needsRatingsUpgrade(applied)) return applied
        val year = if (applied.type == "movie") applied.subtitle.take(4) else null
        val (imdb, rt) = fetchOmdbRatings(applied.title, year)
        if (imdb == null && rt == null) return applied
        val upgraded = applied.copy(imdbRating = imdb, rottenTomatoesRating = rt)
        saveCachedVideoMetadata(context, item.video.path, upgraded)
        return upgraded
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

            // OMDB ratings for the show
            val (imdb, rt) =
                if (tv != null) fetchOmdbRatings(tv.name ?: episodeInfo.showName, null)
                else null to null

            item.copy(
                title = tv?.name ?: episodeInfo.showName,
                subtitle =
                    "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')} • ${episodeDetails?.name ?: ""}",
                posterUrl =
                    tv?.poster_path?.let {
                        "https://image.tmdb.org/t/p/w780$it"
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
                imdbRating = imdb ?: item.imdbRating,
                rottenTomatoesRating = rt ?: item.rottenTomatoesRating,
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

                // OMDB ratings for the movie (title + year gives the best match)
                val (imdb, rt) =
                    if (movie != null) fetchOmdbRatings(movie.title ?: movieSearchName, movie.release_date?.take(4))
                    else null to null

                item.copy(
                    title = movie?.title ?: item.title,
                    subtitle = movie?.release_date?.take(4) ?: item.subtitle,
                    posterUrl =
                        movie?.poster_path?.let {
                            "https://image.tmdb.org/t/p/w780$it"
                        },
                    backdropUrl =
                        movie?.backdrop_path?.let {
                            "https://image.tmdb.org/t/p/original$it"
                        },
                    overview = movie?.overview ?: item.overview,
                    rating = movie?.vote_average ?: item.rating,
                    imdbRating = imdb ?: item.imdbRating,
                    rottenTomatoesRating = rt ?: item.rottenTomatoesRating,
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
