package com.sole.cinevault.metadata

import android.content.Context
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.extractEpisodeInfo
import com.sole.cinevault.library.saveLibraryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MetadataLanguageOption(val code: String, val label: String)

val supportedMetadataLanguages = listOf(
    MetadataLanguageOption("en-US", "English"),
    MetadataLanguageOption("es-ES", "Spanish"),
    MetadataLanguageOption("fr-FR", "French"),
    MetadataLanguageOption("de-DE", "German"),
    MetadataLanguageOption("it-IT", "Italian"),
    MetadataLanguageOption("pt-BR", "Portuguese"),
    MetadataLanguageOption("hi-IN", "Hindi"),
    MetadataLanguageOption("ja-JP", "Japanese"),
    MetadataLanguageOption("ko-KR", "Korean"),
    MetadataLanguageOption("zh-CN", "Chinese")
)

private const val METADATA_SETTINGS_PREFS = "cinevault_metadata_settings"
private const val METADATA_LANGUAGE_KEY = "metadata_language"

fun loadMetadataLanguage(context: Context): String =
    context.getSharedPreferences(METADATA_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(METADATA_LANGUAGE_KEY, "en-US")
        ?.takeIf { code -> supportedMetadataLanguages.any { it.code == code } }
        ?: "en-US"

fun saveMetadataLanguage(context: Context, language: String) {
    val safeLanguage = language.takeIf { code -> supportedMetadataLanguages.any { it.code == code } }
        ?: "en-US"
    context.getSharedPreferences(METADATA_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(METADATA_LANGUAGE_KEY, safeLanguage)
        .apply()
}

fun metadataArtworkLanguage(context: Context): String =
    loadMetadataLanguage(context).substringBefore('-').lowercase()

data class MetadataOperationProgress(
    val completed: Int,
    val total: Int,
    val message: String
) {
    val fraction: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

suspend fun refreshAllMissingMetadata(
    context: Context,
    videos: List<VideoWithMetadata>,
    onProgress: suspend (MetadataOperationProgress) -> Unit = {}
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {
    val targetIndexes = videos.indices.filter { index ->
        val item = videos[index]
        item.type != "restricted" && shouldEnrichOnlineMetadata(item)
    }
    if (targetIndexes.isEmpty()) {
        withContext(Dispatchers.Main) {
            onProgress(MetadataOperationProgress(0, 0, "Nothing is missing"))
        }
        return@withContext videos
    }

    val updated = videos.toMutableList()
    targetIndexes.forEachIndexed { position, videoIndex ->
        val item = updated[videoIndex]
        updated[videoIndex] = try {
            enrichVideoWithOnlineMetadata(context, item)
        } catch (_: Exception) {
            item
        }
        withContext(Dispatchers.Main) {
            onProgress(
                MetadataOperationProgress(
                    completed = position + 1,
                    total = targetIndexes.size,
                    message = "Checking ${item.video.name}"
                )
            )
        }
    }
    saveLibraryCache(context, updated)
    updated
}

suspend fun refreshAllLibraryArtwork(
    context: Context,
    videos: List<VideoWithMetadata>,
    onProgress: suspend (MetadataOperationProgress) -> Unit = {}
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {
    val groups = videos.withIndex()
        .filter { (_, item) -> item.tmdbId != null && (item.type == "movie" || item.type == "tv") }
        .groupBy { (_, item) -> "${item.type}:${item.tmdbId}" }
        .values
        .toList()

    if (groups.isEmpty()) {
        withContext(Dispatchers.Main) {
            onProgress(MetadataOperationProgress(0, 0, "No matched artwork to refresh"))
        }
        return@withContext videos
    }

    val updated = videos.toMutableList()
    groups.forEachIndexed { groupPosition, indexedItems ->
        val representative = indexedItems.first().value
        try {
            if (representative.type == "tv") {
                val refreshed = refreshTvArtwork(context, indexedItems.map { it.value })
                indexedItems.zip(refreshed).forEach { (indexed, item) ->
                    updated[indexed.index] = item
                }
            } else {
                indexedItems.forEach { indexed ->
                    updated[indexed.index] = refreshArtwork(context, indexed.value)
                }
            }
        } catch (_: Exception) {
            // A single unavailable provider/title must not abort the rest.
        }
        withContext(Dispatchers.Main) {
            onProgress(
                MetadataOperationProgress(
                    completed = groupPosition + 1,
                    total = groups.size,
                    message = "Refreshing ${representative.title}"
                )
            )
        }
    }
    saveLibraryCache(context, updated)
    updated
}

/**
 * Removes downloaded metadata only. Video files, playback positions,
 * favorites, Secret Folder records and hand-picked artwork remain intact.
 */
suspend fun clearAutomaticMetadataCache(
    context: Context,
    videos: List<VideoWithMetadata>
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {
    val database = CachedVideoMetadataDatabase.getInstance(context)
    database.cachedVideoMetadataDao().clearAll()
    database.artworkPreferenceDao().resetAutomaticAttemptTimestamps()

    val reset = videos.map { item ->
        if (item.type == "restricted" || item.type == "local") {
            item
        } else {
            val episode = extractEpisodeInfo(item.video.name)
            val bare = item.copy(
                title = episode?.showName ?: cleanMovieFilename(item.video.name),
                subtitle = episode?.let {
                    "S${it.season.toString().padStart(2, '0')}E${it.episode.toString().padStart(2, '0')}"
                } ?: "",
                posterUrl = null,
                backdropUrl = null,
                episodeStill = null,
                overview = null,
                rating = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                tmdbId = null,
                genres = emptyList(),
                director = null,
                collectionId = null,
                collectionName = null,
                curatedCollections = emptyList(),
                cast = emptyList()
            )
            applyManualArtworkPreference(context, bare)
        }
    }
    saveLibraryCache(context, reset)
    reset
}
