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
    val type: String,
    // ── Media intelligence additions ───────────────────────────────────────
    // IMPORTANT: these are nullable, not defaulted-non-null, on purpose.
    // Gson bypasses the Kotlin constructor when deserializing (it uses
    // reflection to set fields directly), so it does NOT respect Kotlin
    // default parameter values for keys missing from old cached JSON —
    // they'd silently come back as null even with a `= emptyList()`
    // declared here. Keeping them nullable and normalizing on read (see
    // applyCachedVideoMetadata below) avoids a crash the first time an
    // old cached entry (from before this feature existed) gets loaded.
    val genres: List<String>? = null,
    val director: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val curatedCollections: List<String>? = null,
    val cast: List<CastEntry>? = null
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
            type = item.type,
            genres = item.genres,
            director = item.director,
            collectionId = item.collectionId,
            collectionName = item.collectionName,
            curatedCollections = item.curatedCollections,
            cast = item.cast
        )

    context
        .getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(videoPath, Gson().toJson(cached))
        .apply()
}

// Upgrades any backdrop URL that still points at TMDB's oversized "original"
// size — this handles items that were cached BEFORE the w1280 fix below,
// with zero network cost (just a string swap on an already-known URL, not a
// re-fetch), so existing libraries benefit immediately without a rescan.
private fun preferredBackdropUrl(url: String?): String? {
    if (url == null) return null
    return url.replace("/t/p/original/", "/t/p/w1280/")
}

fun applyCachedVideoMetadata(
    item: VideoWithMetadata,
    cached: CachedVideoMetadata
): VideoWithMetadata {
    return item.copy(
        title = cached.title,
        subtitle = cached.subtitle,
        posterUrl = cached.posterUrl,
        backdropUrl = preferredBackdropUrl(cached.backdropUrl),
        episodeStill = cached.episodeStill,
        overview = cached.overview,
        rating = cached.rating,
        imdbRating = cached.imdbRating,
        rottenTomatoesRating = cached.rottenTomatoesRating,
        tmdbId = cached.tmdbId,
        type = cached.type,
        // Normalized here — see the comment on CachedVideoMetadata.genres.
        genres = cached.genres ?: emptyList(),
        director = cached.director,
        collectionId = cached.collectionId,
        collectionName = cached.collectionName,
        curatedCollections = cached.curatedCollections ?: emptyList(),
        cast = cached.cast ?: emptyList()
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

/**
 * True when a movie/TV item was matched online (has a tmdbId) but predates
 * the media-intelligence feature, so it's missing genres/director/collection.
 * Lets an already-scanned library pick this data up on next load instead of
 * requiring a full rescan.
 */
fun needsGenreUpgrade(item: VideoWithMetadata): Boolean {
    // OR, not AND: an item that already has genres+director from a PRIOR
    // upgrade pass (before the cast field existed) still needs to be
    // revisited to backfill cast — if this were AND, such items would never
    // trigger again since genres/director are no longer empty.
    return (item.type == "movie" || item.type == "tv") &&
            (item.tmdbId ?: 0) > 0 &&
            (item.genres.isEmpty() || item.director.isNullOrBlank() || item.cast.isEmpty())
}

// ── OMDB — the source of IMDb and Rotten Tomatoes ratings ─────────────────────
// THE FIX: this was never called before. TMDB provides posters/cast/its own
// score, but IMDb and RT ratings only come from OMDB.

private val omdbHttpClient by lazy { OkHttpClient() }

suspend fun fetchOmdbRatings(title: String, year: String?): Pair<String?, String?> =
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

// ── Curated collections ─────────────────────────────────────────────────────
// Some franchises aren't a single native TMDB "collection" (Harry Potter and
// Mission Impossible are; the Marvel Cinematic Universe is fragmented across
// ~15+ separate sub-collections and many standalone films that belong to no
// collection at all). Matching by TMDB KEYWORD NAME instead of a hardcoded
// movie-ID list means this stays accurate as new films release — no manual
// list to maintain, no risk of a stale/wrong hardcoded ID. To add another
// curated grouping later (e.g. a DC one), just add another line here.
private data class CuratedCollectionDefinition(val displayName: String, val matchKeyword: String)

private val curatedCollectionDefinitions = listOf(
    CuratedCollectionDefinition("Marvel Cinematic Universe", "marvel cinematic universe")
)

private fun matchCuratedCollections(keywordNames: List<String>): List<String> {
    if (keywordNames.isEmpty()) return emptyList()
    val lowerKeywords = keywordNames.map { it.lowercase() }
    return curatedCollectionDefinitions
        .filter { def -> lowerKeywords.any { it == def.matchKeyword } }
        .map { it.displayName }
}


// Small holder for the extra fields pulled from the /movie/{id} and
// /tv/{id} "details" endpoints (with credits appended). Kept separate from
// the DTOs themselves so the enrichment code below doesn't care whether the
// source was a movie or a TV show.
data class TmdbExtraDetails(
    val genres: List<String>,
    val director: String?,
    val collectionId: Int?,
    val collectionName: String?,
    val curatedCollections: List<String>,
    val cast: List<CastEntry>
)

fun extractTopCast(credits: TmdbCreditsBlock?): List<CastEntry> {
    return credits?.cast
        ?.mapNotNull { c ->
            val id = c.id
            val name = c.name
            if (id != null && !name.isNullOrBlank()) CastEntry(id, name, c.profile_path) else null
        }
        ?.take(10)
        ?: emptyList()
}

suspend fun fetchTmdbExtraDetails(tmdbId: Int, type: String): TmdbExtraDetails? =
    withContext(Dispatchers.IO) {
        try {
            if (type == "tv") {
                val details = TmdbClient.api.getTvDetails(BuildConfig.TMDB_TOKEN, tmdbId)
                val keywordNames = details.keywords?.results?.mapNotNull { it.name } ?: emptyList()
                TmdbExtraDetails(
                    genres = details.genres?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } } ?: emptyList(),
                    director = details.created_by?.firstOrNull()?.name,
                    collectionId = null, // TV shows don't have TMDB "collections"
                    collectionName = null,
                    curatedCollections = matchCuratedCollections(keywordNames),
                    cast = extractTopCast(details.credits)
                )
            } else {
                val details = TmdbClient.api.getMovieDetails(BuildConfig.TMDB_TOKEN, tmdbId)
                val keywordNames = details.keywords?.keywords?.mapNotNull { it.name } ?: emptyList()
                TmdbExtraDetails(
                    genres = details.genres?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } } ?: emptyList(),
                    director = details.credits?.crew?.firstOrNull { it.job == "Director" }?.name,
                    collectionId = details.belongs_to_collection?.id,
                    collectionName = details.belongs_to_collection?.name,
                    curatedCollections = matchCuratedCollections(keywordNames),
                    cast = extractTopCast(details.credits)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

suspend fun enrichVideoWithOnlineMetadata(
    context: Context,
    item: VideoWithMetadata
): VideoWithMetadata {

    loadCachedVideoMetadata(context, item.video.path)?.let { cached ->
        var applied = applyCachedVideoMetadata(item, cached)
        val needsRatings = needsRatingsUpgrade(applied)
        val needsGenres = needsGenreUpgrade(applied)

        if (!needsRatings && !needsGenres) return applied

        if (needsRatings) {
            val year = if (applied.type == "movie") applied.subtitle.take(4) else null
            val (imdb, rt) = fetchOmdbRatings(applied.title, year)
            if (imdb != null || rt != null) {
                applied = applied.copy(imdbRating = imdb ?: applied.imdbRating, rottenTomatoesRating = rt ?: applied.rottenTomatoesRating)
            }
        }
        if (needsGenres) {
            val tmdbId = applied.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                fetchTmdbExtraDetails(tmdbId, applied.type)?.let { extra ->
                    applied = applied.copy(
                        genres = extra.genres,
                        director = extra.director,
                        collectionId = extra.collectionId,
                        collectionName = extra.collectionName,
                        curatedCollections = extra.curatedCollections,
                        cast = extra.cast
                    )
                }
            }
        }

        saveCachedVideoMetadata(context, item.video.path, applied)
        return applied
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

            // Genres/creator for the show — same details call used by the
            // upgrade path above, just inlined here for a freshly-scanned item.
            val extra = tv?.id?.let { fetchTmdbExtraDetails(it, "tv") }

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
                        // w1280 instead of "original" — TMDB's original-size
                        // backdrops are often several MB; w1280 looks
                        // identical on any phone/tablet screen and downloads
                        // far faster, which is what was causing the ~5s
                        // first-load delay on the Detail screen's hero image.
                        "https://image.tmdb.org/t/p/w1280$it"
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
                type = "tv",
                genres = extra?.genres ?: item.genres,
                director = extra?.director ?: item.director,
                collectionId = extra?.collectionId ?: item.collectionId,
                collectionName = extra?.collectionName ?: item.collectionName,
                curatedCollections = extra?.curatedCollections ?: item.curatedCollections,
                cast = extra?.cast ?: item.cast
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

                // Genres/director/collection — same details call used by the
                // upgrade path above, just inlined here for a freshly-scanned item.
                val extra = movie?.id?.let { fetchTmdbExtraDetails(it, "movie") }

                item.copy(
                    title = movie?.title ?: item.title,
                    subtitle = movie?.release_date?.take(4) ?: item.subtitle,
                    posterUrl =
                        movie?.poster_path?.let {
                            "https://image.tmdb.org/t/p/w780$it"
                        },
                    backdropUrl =
                        movie?.backdrop_path?.let {
                            "https://image.tmdb.org/t/p/w1280$it"
                        },
                    overview = movie?.overview ?: item.overview,
                    rating = movie?.vote_average ?: item.rating,
                    imdbRating = imdb ?: item.imdbRating,
                    rottenTomatoesRating = rt ?: item.rottenTomatoesRating,
                    tmdbId = movie?.id ?: item.tmdbId,
                    type = "movie",
                    genres = extra?.genres ?: item.genres,
                    director = extra?.director ?: item.director,
                    collectionId = extra?.collectionId ?: item.collectionId,
                    collectionName = extra?.collectionName ?: item.collectionName,
                    curatedCollections = extra?.curatedCollections ?: item.curatedCollections,
                    cast = extra?.cast ?: item.cast
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
