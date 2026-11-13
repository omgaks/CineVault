package com.sole.cinevault.metadata

import android.content.Context
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.library.CatalogEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full episode list of a show from TMDB, cached for a few days, used to tell the person
 * which episodes they have not downloaded. Reads the cache first; goes online only when the
 * cache is missing or old and online metadata is switched on.
 */
object ShowCatalogRepository {
    private const val PREFS = "cinevault_show_catalog"
    private const val TTL_MS = 3L * 24 * 60 * 60 * 1000
    private const val MAX_SEASONS = 40

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun load(context: Context, tmdbId: Int): List<CatalogEpisode>? = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(tmdbId.toString(), null)?.let { decode(it) }
        val fresh = cached != null && System.currentTimeMillis() - cached.first < TTL_MS
        if (fresh) return@withContext cached?.second
        if (!loadMetadataFetchEnabled(context) || BuildConfig.TMDB_TOKEN.isBlank()) return@withContext cached?.second
        val fetched = try {
            val language = loadMetadataLanguage(context)
            val details = TmdbClient.api.getTvDetails(BuildConfig.TMDB_TOKEN, tmdbId, language = language)
            val out = mutableListOf<CatalogEpisode>()
            details.seasons.orEmpty()
                .mapNotNull { it.season_number }
                .filter { it in 1..MAX_SEASONS }
                .forEach { n ->
                    TmdbClient.api.getTvSeason(BuildConfig.TMDB_TOKEN, tmdbId, n, language).episodes.orEmpty()
                        .forEach { e ->
                            val num = e.episode_number ?: return@forEach
                            out += CatalogEpisode(n, num, e.name.orEmpty(), e.air_date)
                        }
                }
            out
        } catch (_: Exception) {
            null
        }
        if (fetched == null) return@withContext cached?.second
        prefs.edit().putString(tmdbId.toString(), encode(fetched)).apply()
        fetched
    }

    private fun encode(list: List<CatalogEpisode>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("s", it.season).put("n", it.number).put("t", it.name).put("a", it.airDate ?: ""))
        }
        return JSONObject().put("at", System.currentTimeMillis()).put("e", arr).toString()
    }

    private fun decode(raw: String): Pair<Long, List<CatalogEpisode>>? = try {
        val obj = JSONObject(raw)
        val arr = obj.getJSONArray("e")
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CatalogEpisode(o.getInt("s"), o.getInt("n"), o.optString("t"), o.optString("a").ifBlank { null })
        }
        obj.getLong("at") to list
    } catch (_: Exception) {
        null
    }
}
