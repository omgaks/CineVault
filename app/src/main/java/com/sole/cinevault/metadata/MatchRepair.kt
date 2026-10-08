package com.sole.cinevault.metadata

import android.content.Context
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.extractEpisodeInfo
import com.sole.cinevault.library.saveLibraryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DoubtfulMatch(val item: VideoWithMetadata, val audit: MatchAudit)

/** Matches worth a look: unmatched, or whose title/year does not resemble the file. One row per show. */
fun findMatchProblems(context: Context, videos: List<VideoWithMetadata>): List<DoubtfulMatch> {
    val checkTitle = loadMetadataLanguage(context).startsWith("en")
    return videos
        .mapNotNull { item ->
            if (item.type != "movie" && item.type != "tv") return@mapNotNull null
            val audit = auditMatch(item.video.name, item.type, item.tmdbId, item.title, item.subtitle, checkTitle)
            if (audit.health == MatchHealth.OK) null else DoubtfulMatch(item, audit)
        }
        .distinctBy { if (it.item.type == "tv") "tv:${it.item.title}" else it.item.video.path }
        .sortedWith(compareBy({ it.audit.health.ordinal }, { it.item.title.lowercase() }))
}

/** Builds a rematch candidate from a TMDB id, e.g. one read from an NFO file. */
suspend fun candidateFromTmdbId(context: Context, tmdbId: Int): MatchCandidate? = withContext(Dispatchers.IO) {
    if (BuildConfig.TMDB_TOKEN.isBlank()) return@withContext null
    runCatching {
        val d = TmdbClient.api.getMovieDetails(BuildConfig.TMDB_TOKEN, tmdbId, language = loadMetadataLanguage(context))
        MatchCandidate(
            tmdbId = d.id ?: tmdbId,
            title = d.title ?: return@runCatching null,
            releaseYear = d.release_date?.take(4)?.toIntOrNull(),
            posterPath = d.poster_path,
            backdropPath = d.backdrop_path,
            overview = d.overview,
            voteAverage = d.vote_average
        )
    }.getOrNull()
}

/**
 * Forgets the downloaded match for [targets] (shows expand to all their
 * episodes) and searches again. Hand-picked artwork is kept, like the
 * existing "clear metadata" action. Needs metadata fetching to be on.
 */
suspend fun rematchAutomatically(
    context: Context,
    videos: List<VideoWithMetadata>,
    targets: List<VideoWithMetadata>,
    onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {
    if (!loadMetadataFetchEnabled(context)) return@withContext videos
    val shows = targets.filter { it.type == "tv" }.map { it.title }.toSet()
    val paths = targets.filter { it.type != "tv" }.map { it.video.path }.toSet()
    val affected = videos.filter { (it.type == "tv" && it.title in shows) || it.video.path in paths }
    if (affected.isEmpty()) return@withContext videos

    val dao = CachedVideoMetadataDatabase.getInstance(context).cachedVideoMetadataDao()
    val result = HashMap<String, VideoWithMetadata>()
    affected.forEachIndexed { i, item ->
        onProgress(i, affected.size)
        runCatching {
            dao.deleteByPath(item.video.path)
            val episode = extractEpisodeInfo(item.video.name)
            val bare = item.copy(
                title = episode?.showName ?: cleanMovieFilename(item.video.name),
                subtitle = episode?.let { "S${it.season.toString().padStart(2, '0')}E${it.episode.toString().padStart(2, '0')}" } ?: "",
                posterUrl = null, backdropUrl = null, episodeStill = null, overview = null, rating = null,
                imdbRating = null, rottenTomatoesRating = null, tmdbId = null, genres = emptyList(), director = null,
                collectionId = null, collectionName = null, curatedCollections = emptyList(), cast = emptyList()
            )
            result[item.video.path] = applyManualArtworkPreference(context, enrichVideoWithOnlineMetadata(context, bare))
        }
    }
    onProgress(affected.size, affected.size)
    val merged = videos.map { result[it.video.path] ?: it }
    saveLibraryCache(context, merged)
    merged
}
