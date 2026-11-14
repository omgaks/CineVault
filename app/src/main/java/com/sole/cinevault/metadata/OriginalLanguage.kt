package com.sole.cinevault.metadata

import android.content.Context
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The original language of a matched title ("ja", "zh", "ko", "en"…), used to tell
 * Japanese/Chinese/Korean animation (anime look) from western animation. Read from the
 * local cache first; fetched from TMDB once and saved. Returns null when unknown or offline.
 */
suspend fun resolveOriginalLanguage(context: Context, item: VideoWithMetadata?): String? {
    if (item == null) return null
    item.originalLanguage?.takeIf { it.isNotBlank() }?.let { return it }
    val tmdbId = item.tmdbId?.takeIf { it > 0 } ?: return null
    return withContext(Dispatchers.IO) {
        val dao = CachedVideoMetadataDatabase.getInstance(context).animationMetadataDao()
        val path = item.video.path
        dao.getByPath(path)?.takeIf { it.tmdbId == tmdbId }?.originalLanguage?.let { return@withContext it }
        if (!loadMetadataFetchEnabled(context) || BuildConfig.TMDB_TOKEN.isBlank()) return@withContext null
        val language = try {
            if (item.type == "tv") {
                TmdbClient.api.getTvDetails(BuildConfig.TMDB_TOKEN, tmdbId).original_language
            } else {
                TmdbClient.api.getMovieDetails(BuildConfig.TMDB_TOKEN, tmdbId).original_language
            }
        } catch (_: Exception) {
            null
        }
        if (!language.isNullOrBlank()) {
            dao.upsert(
                AnimationMetadata(
                    videoPath = path,
                    tmdbId = tmdbId,
                    originalLanguage = language,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        language
    }
}
