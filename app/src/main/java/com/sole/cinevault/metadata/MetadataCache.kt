package com.sole.cinevault.metadata

import com.sole.cinevault.BuildConfig
import com.sole.cinevault.library.extractEpisodeInfo
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.CastEntry

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

private const val METADATA_PREFS_NAME = "cinevault_metadata_cache"

// ── Privacy: disable-metadata toggle ──────────────────────────────────────
// Separate SharedPreferences file from the metadata CACHE above on purpose
// — this is a person's setting, not cached data, and shouldn't ever get
// wiped by a "clear metadata cache" action elsewhere in the app. Defaults
// to enabled (true) so nothing changes for anyone who's never touched this
// setting — this is an off switch someone opts into, not a behavior change
// forced on existing installs.
private const val METADATA_SETTINGS_PREFS_NAME = "cinevault_metadata_settings"
private const val METADATA_FETCH_ENABLED_KEY = "metadata_fetch_enabled"

fun loadMetadataFetchEnabled(context: Context): Boolean {
    return context.getSharedPreferences(METADATA_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(METADATA_FETCH_ENABLED_KEY, true)
}

fun saveMetadataFetchEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(METADATA_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(METADATA_FETCH_ENABLED_KEY, enabled)
        .apply()
}

// FIX: this is now the Room Entity backing cinevault_metadata_cache's
// replacement (see CachedVideoMetadataDatabase.kt) — one SharedPreferences
// KEY per video was the single worst SharedPreferences-as-database offender
// in the app (genuinely unbounded key count as a library grows, the real
// ANR/TransactionTooLargeException risk, not just an awkward fit). videoPath
// is new — previously it only existed as the external SharedPreferences
// key, never as a field inside the stored value itself; Room needs it as an
// actual column to serve as the primary key.
@Entity(tableName = "cached_video_metadata")
data class CachedVideoMetadata(
    @PrimaryKey val videoPath: String,
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
    // Room itself doesn't have this same bypass-the-constructor issue —
    // kept nullable anyway for consistency with the values already on
    // disk from the SharedPreferences era, migrated as-is.
    val genres: List<String>? = null,
    val director: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val curatedCollections: List<String>? = null,
    val cast: List<CastEntry>? = null
)

// FIX: one-time migration off cinevault_metadata_cache (one
// SharedPreferences key per video — the genuine unbounded-growth risk,
// not just an awkward fit) into Room. Guarded by a persisted flag in the
// existing settings store (not the cache store itself, same reasoning as
// the disable-metadata toggle above — a migration-done marker is a
// setting, not cache data) so this only ever actually scans the legacy
// prefs file once per install, not on every single video load. An
// in-memory flag alone wouldn't survive a process restart; this does.
private const val METADATA_ROOM_MIGRATION_DONE_KEY = "metadata_room_migration_done"

private suspend fun ensureMetadataMigratedToRoom(context: Context) = withContext(Dispatchers.IO) {
    val settingsPrefs = context.getSharedPreferences(METADATA_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    if (settingsPrefs.getBoolean(METADATA_ROOM_MIGRATION_DONE_KEY, false)) return@withContext

    val legacyPrefs = context.getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)
    val legacyEntries = legacyPrefs.all
    if (legacyEntries.isNotEmpty()) {
        val dao = CachedVideoMetadataDatabase.getInstance(context).cachedVideoMetadataDao()
        val migrated = mutableListOf<CachedVideoMetadata>()
        for ((videoPath, rawValue) in legacyEntries) {
            val json = rawValue as? String ?: continue
            try {
                // Old cached JSON has no videoPath field at all — it only
                // ever existed as the SharedPreferences KEY, never as
                // part of the stored value. Gson's reflection-based
                // deserialization (see the Entity's own doc comment)
                // would leave the new non-nullable videoPath field as an
                // unreliable value here regardless — copy() overwrites
                // it with the trusted value from the legacy key itself
                // immediately after, so that unreliable intermediate
                // value is never actually used for anything.
                val parsed = Gson().fromJson(json, CachedVideoMetadata::class.java)
                migrated.add(parsed.copy(videoPath = videoPath))
            } catch (_: Exception) {
                // Skip a corrupted individual entry rather than fail the
                // whole migration over one bad row — same "don't lose
                // everything over one bad apple" reasoning as everywhere
                // else data gets migrated in this app.
            }
        }
        if (migrated.isNotEmpty()) dao.upsertAll(migrated)
        legacyPrefs.edit().clear().apply()
    }
    settingsPrefs.edit().putBoolean(METADATA_ROOM_MIGRATION_DONE_KEY, true).apply()
}

suspend fun loadCachedVideoMetadata(
    context: Context,
    videoPath: String
): CachedVideoMetadata? = withContext(Dispatchers.IO) {
    ensureMetadataMigratedToRoom(context)
    CachedVideoMetadataDatabase.getInstance(context).cachedVideoMetadataDao().getByPath(videoPath)
}

suspend fun saveCachedVideoMetadata(
    context: Context,
    videoPath: String,
    item: VideoWithMetadata
) = withContext(Dispatchers.IO) {
    ensureMetadataMigratedToRoom(context)
    val cached =
        CachedVideoMetadata(
            videoPath = videoPath,
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
    CachedVideoMetadataDatabase.getInstance(context).cachedVideoMetadataDao().upsert(cached)
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

suspend fun applyCachedMetadataIfAvailable(
    context: Context,
    item: VideoWithMetadata
): VideoWithMetadata {
    val cached = loadCachedVideoMetadata(context, item.video.path)
    val cachedItem = if (cached != null) {
        applyCachedVideoMetadata(item, cached)
    } else {
        item
    }
    return applyManualArtworkPreference(context, cachedItem)
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

/** True when a matched title is missing either of its useful artwork sizes. */
fun needsArtworkUpgrade(item: VideoWithMetadata): Boolean {
    return (item.type == "movie" || item.type == "tv") &&
            (item.tmdbId ?: 0) > 0 &&
            (item.posterUrl.isNullOrBlank() || item.backdropUrl.isNullOrBlank())
}

/**
 * Decides whether the library scanner should make an online metadata pass.
 * Personal/camera videos are deliberately excluded after their first lookup;
 * otherwise they would be retried on every scan because they have no poster.
 */
fun shouldEnrichOnlineMetadata(item: VideoWithMetadata): Boolean {
    if (item.type == "local") return false
    return !hasUsefulOnlineMetadata(item) ||
            needsRatingsUpgrade(item) ||
            needsGenreUpgrade(item) ||
            needsArtworkUpgrade(item)
}

private suspend fun fetchAutomaticFanartArtwork(
    context: Context,
    videoPath: String,
    tmdbId: Int,
    type: String
): FanartArtwork? {
    if (!canAttemptAutomaticArtwork(context, videoPath)) return null
    recordAutomaticArtworkAttempt(context, videoPath)
    return fetchFanartArtwork(tmdbId, type)
}

private suspend fun fillMissingArtwork(context: Context, item: VideoWithMetadata): VideoWithMetadata {
    if (!needsArtworkUpgrade(item)) return item
    val tmdbId = item.tmdbId ?: return item
    val fanart = fetchAutomaticFanartArtwork(context, item.video.path, tmdbId, item.type) ?: return item
    return item.copy(
        posterUrl = item.posterUrl?.takeIf { it.isNotBlank() } ?: fanart.posterUrl,
        backdropUrl = item.backdropUrl?.takeIf { it.isNotBlank() } ?: fanart.backdropUrl
    )
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

    // Privacy: when off, this function makes NO network calls at all —
    // not the initial search, not the OMDB ratings lookup, and not the
    // "upgrade" path below either (which also calls out live even on a
    // cache hit, if some fields are missing). Only whatever's already
    // cached gets applied; a video with nothing cached yet just keeps its
    // bare filename-derived title. This is a deliberate off switch someone
    // opts into, not the feature being removed — CineVault's poster/rating
    // enrichment is core to the app, but the choice to use it belongs to
    // the person, not something forced.
    if (!loadMetadataFetchEnabled(context)) {
        return applyCachedMetadataIfAvailable(context, item)
    }

    val cachedMetadata = loadCachedVideoMetadata(context, item.video.path)
    if (cachedMetadata != null) {
        val cached = cachedMetadata
        var applied = applyCachedVideoMetadata(item, cached)
        val needsRatings = needsRatingsUpgrade(applied)
        val needsGenres = needsGenreUpgrade(applied)
        val displayed = applyManualArtworkPreference(context, applied)
        val needsArtwork = needsArtworkUpgrade(displayed)

        // A previous failed TMDB search was cached as a bare item. Those
        // entries used to return here forever, so corrected filenames and
        // newly available results could never recover. Let them fall through
        // to the fresh search below. Known personal videos remain cached.
        val previousLookupFailed = !hasUsefulOnlineMetadata(applied) && applied.type != "local"
        if (!previousLookupFailed && !needsRatings && !needsGenres && !needsArtwork) return displayed

        if (!previousLookupFailed && needsRatings) {
            val year = if (applied.type == "movie") applied.subtitle.take(4) else null
            val (imdb, rt) = fetchOmdbRatings(applied.title, year)
            if (imdb != null || rt != null) {
                applied = applied.copy(imdbRating = imdb ?: applied.imdbRating, rottenTomatoesRating = rt ?: applied.rottenTomatoesRating)
            }
        }
        if (!previousLookupFailed && needsGenres) {
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

        if (!previousLookupFailed && needsArtwork) {
            applied = fillMissingArtwork(context, applied)
        }

        if (!previousLookupFailed) {
            saveCachedVideoMetadata(context, item.video.path, applied)
            return applyManualArtworkPreference(context, applied)
        }
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

            val tmdbPoster = tv?.poster_path?.let { "https://image.tmdb.org/t/p/w780$it" }
            val tmdbBackdrop = tv?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
            val fanart = if (tv?.id != null && (tmdbPoster == null || tmdbBackdrop == null)) {
                fetchAutomaticFanartArtwork(context, item.video.path, tv.id, "tv")
            } else null

            item.copy(
                title = tv?.name ?: episodeInfo.showName,
                subtitle =
                    "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')} • ${episodeDetails?.name ?: ""}",
                posterUrl = tmdbPoster ?: fanart?.posterUrl,
                backdropUrl = tmdbBackdrop ?: fanart?.backdropUrl,
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
                val yearHint = extractYearHint(item.video.name)
                val yearFilteredResults = try {
                    TmdbClient.api.searchMovie(
                        bearerToken = BuildConfig.TMDB_TOKEN,
                        query = movieSearchName,
                        primaryReleaseYear = yearHint
                    ).results
                } catch (_: Exception) {
                    emptyList()
                }
                val movieResults = if (yearFilteredResults.isNotEmpty() || yearHint == null) {
                    yearFilteredResults
                } else {
                    try {
                        TmdbClient.api.searchMovie(
                            bearerToken = BuildConfig.TMDB_TOKEN,
                            query = movieSearchName
                        ).results
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val movie = selectBestMovieMatch(movieResults, movieSearchName, yearHint)

                // OMDB ratings for the movie (title + year gives the best match)
                val (imdb, rt) =
                    if (movie != null) fetchOmdbRatings(movie.title ?: movieSearchName, movie.release_date?.take(4))
                    else null to null

                // Genres/director/collection — same details call used by the
                // upgrade path above, just inlined here for a freshly-scanned item.
                val extra = movie?.id?.let { fetchTmdbExtraDetails(it, "movie") }

                val tmdbPoster = movie?.poster_path?.let { "https://image.tmdb.org/t/p/w780$it" }
                val tmdbBackdrop = movie?.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                val fanart = if (movie?.id != null && (tmdbPoster == null || tmdbBackdrop == null)) {
                    fetchAutomaticFanartArtwork(context, item.video.path, movie.id, "movie")
                } else null

                item.copy(
                    title = movie?.title ?: item.title,
                    subtitle = movie?.release_date?.take(4) ?: item.subtitle,
                    posterUrl = tmdbPoster ?: fanart?.posterUrl,
                    backdropUrl = tmdbBackdrop ?: fanart?.backdropUrl,
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
    return applyManualArtworkPreference(context, enriched)
}

private fun normalizeMetadataTitle(value: String): String {
    return value.lowercase().filter { it.isLetterOrDigit() }
}

private fun selectBestMovieMatch(
    results: List<TmdbMovie>,
    cleanedTitle: String,
    yearHint: String?
): TmdbMovie? {
    val wantedTitle = normalizeMetadataTitle(cleanedTitle)
    return results.maxByOrNull { candidate ->
        var score = 0
        if (normalizeMetadataTitle(candidate.title.orEmpty()) == wantedTitle) score += 100
        if (yearHint != null && candidate.release_date?.startsWith(yearHint) == true) score += 50
        if (!candidate.poster_path.isNullOrBlank()) score += 5
        if (!candidate.backdrop_path.isNullOrBlank()) score += 3
        score
    }
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
