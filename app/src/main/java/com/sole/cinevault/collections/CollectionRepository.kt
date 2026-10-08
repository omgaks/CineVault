package com.sole.cinevault.collections

import android.content.Context
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.metadata.TmdbClient
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import com.sole.cinevault.metadata.tmdbAuthorizationHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CollectionLoad(
    val details: CollectionDetails,
    val fetchedAtMs: Long,
    /** Cached copy older than the TTL that couldn't be refreshed (offline / lookups off). */
    val isStale: Boolean
)

/** Lazily-fetched extras for one film, shown in the missing-film sheet. */
data class FilmExtras(
    val runtimeMinutes: Int?,
    val rating: Double?,
    val overview: String?,
    val trailerKey: String?,
    val providers: List<String>
)

/**
 * TMDB-only data source for Collections V2.
 *
 *  - Cache-first with a 7-day TTL in cinevault_collections.db.
 *  - Honours the existing "Fetch online metadata" privacy switch: when it is
 *    off, nothing is requested — only what is already cached is shown.
 *  - Goes through TmdbClient (which already handles v3 keys and Bearer tokens).
 *  - Sends only TMDB ids; no paths, filenames or library contents.
 */
object CollectionRepository {
    private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun load(context: Context, collectionId: Int): CollectionLoad? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = CollectionDatabase.get(app).dao()
        val now = System.currentTimeMillis()
        val cached = runCatching { dao.cached(collectionId) }.getOrNull()
        val cachedLoad = cached?.toLoad(isStale = false)

        if (cachedLoad != null && now - cachedLoad.fetchedAtMs < CACHE_TTL_MS) return@withContext cachedLoad

        val canFetch = loadMetadataFetchEnabled(app) && BuildConfig.TMDB_TOKEN.isNotBlank()
        if (!canFetch) return@withContext cachedLoad?.copy(isStale = true)

        val response = runCatching {
            TmdbClient.api.getCollection(tmdbAuthorizationHeader(), collectionId)
        }.getOrNull()

        val parts = response?.parts.orEmpty().mapNotNull { p ->
            val id = p.id ?: return@mapNotNull null
            val title = p.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CollectionPart(id, title, p.release_date?.takeIf { it.isNotBlank() }, p.poster_path, p.overview)
        }
        if (response == null || parts.isEmpty()) return@withContext cachedLoad?.copy(isStale = true)

        val entity = CollectionCacheEntity(
            collectionId = collectionId,
            name = response.name?.takeIf { it.isNotBlank() } ?: cached?.name ?: "Collection",
            overview = response.overview,
            posterPath = response.poster_path,
            backdropPath = response.backdrop_path,
            partsJson = encodeParts(parts),
            fetchedAtMs = now
        )
        runCatching { dao.putCache(entity) }
        entity.toLoad(isStale = false)
    }

    /** Number of already-released films in the collection, for the shelf's "2 of 6". Cache-first. */
    suspend fun releasedTotal(context: Context, collectionId: Int): Int? =
        load(context, collectionId)?.let { CollectionPlanner.releasedCount(it.details.parts, todayIso()) }

    suspend fun filmExtras(context: Context, tmdbId: Int): FilmExtras? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!loadMetadataFetchEnabled(app) || BuildConfig.TMDB_TOKEN.isBlank()) return@withContext null
        val response = runCatching {
            TmdbClient.api.getMovieExtras(tmdbAuthorizationHeader(), tmdbId)
        }.getOrNull() ?: return@withContext null

        val trailers = response.videos?.results.orEmpty().filter {
            it.site.equals("YouTube", ignoreCase = true) &&
                it.type.equals("Trailer", ignoreCase = true) &&
                !it.key.isNullOrBlank()
        }
        val trailerKey = (trailers.firstOrNull { it.official == true } ?: trailers.firstOrNull())?.key

        val region = Locale.getDefault().country.uppercase(Locale.ROOT)
        val regional = response.watchProviders?.results?.get(region)
        val providers = (regional?.flatrate.orEmpty() + regional?.rent.orEmpty() + regional?.buy.orEmpty())
            .mapNotNull { it.provider_name?.takeIf { n -> n.isNotBlank() } }
            .distinct()
            .take(6)

        FilmExtras(
            runtimeMinutes = response.runtime?.takeIf { it > 0 },
            rating = response.vote_average?.takeIf { it > 0.0 },
            overview = response.overview?.takeIf { it.isNotBlank() },
            trailerKey = trailerKey,
            providers = providers
        )
    }

    // ── Wantlist ────────────────────────────────────────────────────────────

    fun wantlistIds(context: Context): Flow<Set<Int>> =
        CollectionDatabase.get(context).dao().wantlistIds().map { it.toSet() }

    suspend fun setWanted(context: Context, part: CollectionPart, collectionId: Int?, wanted: Boolean) =
        withContext(Dispatchers.IO) {
            val dao = CollectionDatabase.get(context).dao()
            if (wanted) {
                dao.addWanted(
                    WantlistEntity(part.tmdbId, part.title, part.posterPath, part.releaseDate, collectionId, System.currentTimeMillis())
                )
            } else {
                dao.removeWanted(part.tmdbId)
            }
        }

    // ── org.json (de)serialisation: no reflection, so release builds can't break old caches ──

    private fun CollectionCacheEntity.toLoad(isStale: Boolean) = CollectionLoad(
        details = CollectionDetails(collectionId, name, overview, posterPath, backdropPath, decodeParts(partsJson)),
        fetchedAtMs = fetchedAtMs,
        isStale = isStale
    )

    private fun encodeParts(parts: List<CollectionPart>): String {
        val array = JSONArray()
        parts.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.tmdbId)
                    .put("title", p.title)
                    .put("date", p.releaseDate ?: JSONObject.NULL)
                    .put("poster", p.posterPath ?: JSONObject.NULL)
                    .put("overview", p.overview ?: JSONObject.NULL)
            )
        }
        return array.toString()
    }

    private fun decodeParts(json: String): List<CollectionPart> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CollectionPart(
                tmdbId = o.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null,
                title = title,
                releaseDate = o.optString("date").takeIf { it.isNotBlank() && it != "null" },
                posterPath = o.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                overview = o.optString("overview").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }.getOrDefault(emptyList())
}
