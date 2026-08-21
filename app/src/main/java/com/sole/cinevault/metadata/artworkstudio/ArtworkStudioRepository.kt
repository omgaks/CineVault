package com.sole.cinevault.metadata.artworkstudio

import android.content.Context
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.metadata.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException

object ArtworkStudioRepository {
    private fun providerError(provider: String, error: Throwable): ArtworkProviderReport {
        val code = (error as? HttpException)?.code()
        val cause = generateSequence(error) { it.cause }.last()
        val message = when (code) {
            401, 403 -> "$provider rejected its API credential"
            404 -> "$provider has no artwork for this saved match"
            429 -> "$provider is temporarily rate-limiting requests"
            else -> cause.message?.takeIf { it.isNotBlank() }
                ?: error.message?.takeIf { it.isNotBlank() }
                ?: "${cause::class.java.simpleName.ifBlank { "Unknown provider error" }}"
        }
        return ArtworkProviderReport(provider, ArtworkProviderStatus.ERROR, message = message)
    }

    /**
     * Loads TMDB's complete image gallery using either supported credential
     * form. Search requests already accepted both v3 keys and v4 tokens, but
     * the Studio previously always constructed a Bearer header here. Keeping
     * the two paths identical avoids an image-only authentication failure.
     */
    private suspend fun loadTmdbOptions(
        item: VideoWithMetadata,
        tmdbId: Int,
        preferredLanguage: String
    ): List<ArtworkOption> {
        val credential = BuildConfig.TMDB_TOKEN.trim()
        require(credential.isNotBlank()) { "TMDB credential is missing from this build" }
        val isV3Key = credential.matches(Regex("^[A-Fa-f0-9]{32}$"))
        val authorization = if (isV3Key) null else credential
            .let { if (it.startsWith("Bearer ", ignoreCase = true)) it else "Bearer $it" }
        val apiKey = credential.takeIf { isV3Key }
        val language = preferredLanguage.substringBefore('-').ifBlank { "en" }
        val response = if (item.type.equals("tv", ignoreCase = true)) {
            TmdbClient.api.getTvImages(authorization, tmdbId, null, apiKey)
        } else {
            TmdbClient.api.getMovieImages(authorization, tmdbId, null, apiKey)
        }
        val posters = response.posters.orEmpty().mapNotNull { image ->
            image.file_path?.let {
                ArtworkOption(
                    url = "https://image.tmdb.org/t/p/w780$it",
                    source = "TMDB",
                    kind = ArtworkKind.POSTER,
                    language = image.iso_639_1,
                    score = (image.vote_average ?: 0.0) + artworkLanguageScore(image.iso_639_1, language)
                )
            }
        }
        val backdrops = response.backdrops.orEmpty().mapNotNull { image ->
            image.file_path?.let {
                ArtworkOption(
                    url = "https://image.tmdb.org/t/p/w1280$it",
                    source = "TMDB",
                    kind = ArtworkKind.BACKDROP,
                    language = image.iso_639_1,
                    score = (image.vote_average ?: 0.0) + artworkLanguageScore(image.iso_639_1, language)
                )
            }
        }
        return posters + backdrops
    }

    suspend fun loadGallery(context: Context, item: VideoWithMetadata): ArtworkStudioGallery =
        withContext(Dispatchers.IO) {
            val tmdbId = item.tmdbId
            if (tmdbId == null || tmdbId <= 0) {
                return@withContext ArtworkStudioGallery(
                    emptyList(),
                    listOf(ArtworkProviderReport("Match", ArtworkProviderStatus.ERROR, message = "Fix the title match before browsing artwork"))
                )
            }
            val language = metadataArtworkLanguage(context)
            coroutineScope {
                val tmdbDeferred = async {
                    runCatching { loadTmdbOptions(item, tmdbId, language) }
                }
                val fanartDeferred = async {
                    if (BuildConfig.FANART_API_KEY.isBlank()) {
                        Result.failure<List<ArtworkOption>>(IllegalStateException("Fanart.tv API key is not configured"))
                    } else {
                        runCatching { fetchFanartArtworkOptionsStrict(tmdbId, item.type, language) }
                    }
                }
                val tmdbResult = tmdbDeferred.await()
                val fanartResult = fanartDeferred.await()
                val tmdb = tmdbResult.getOrDefault(emptyList())
                val fanart = fanartResult.getOrDefault(emptyList())
                // Keep the currently displayed artwork visible in the
                // gallery even when a provider has removed an image or is
                // temporarily unavailable. It is a fallback choice, not a
                // replacement for the provider diagnostics above.
                val currentArtwork = listOfNotNull(
                    item.posterUrl?.let {
                        ArtworkOption(
                            url = it,
                            source = artworkSourceName(it),
                            kind = ArtworkKind.POSTER,
                            score = -1.0
                        )
                    },
                    item.backdropUrl?.let {
                        ArtworkOption(
                            url = it,
                            source = artworkSourceName(it),
                            kind = ArtworkKind.BACKDROP,
                            score = -1.0
                        )
                    }
                )
                val reports = listOf(
                    tmdbResult.fold(
                        onSuccess = { ArtworkProviderReport("TMDB", if (it.isEmpty()) ArtworkProviderStatus.EMPTY else ArtworkProviderStatus.READY, it.size, if (it.isEmpty()) "No images returned" else "${it.size} images") },
                        onFailure = { providerError("TMDB", it) }
                    ),
                    fanartResult.fold(
                        onSuccess = { ArtworkProviderReport("Fanart.tv", if (it.isEmpty()) ArtworkProviderStatus.EMPTY else ArtworkProviderStatus.READY, it.size, if (it.isEmpty()) "No images returned" else "${it.size} images") },
                        onFailure = {
                            if (BuildConfig.FANART_API_KEY.isBlank()) ArtworkProviderReport("Fanart.tv", ArtworkProviderStatus.NOT_CONFIGURED, message = "API key not configured")
                            else providerError("Fanart.tv", it)
                        }
                    )
                )
                ArtworkStudioGallery(
                    options = (tmdb + fanart + currentArtwork)
                        .distinctBy { it.url }
                        .sortedByDescending { it.score },
                    reports = reports
                )
            }
        }

    suspend fun search(context: Context, rawQuery: String, type: String): ArtworkStudioResult<List<StudioMatchCandidate>> =
        withContext(Dispatchers.IO) {
            val query = tmdbMovieSearchQuery(rawQuery).ifBlank { rawQuery.trim() }
            if (query.isBlank()) return@withContext ArtworkStudioResult.Failure("Enter a title to search")
            val language = loadMetadataLanguage(context)
            try {
                val matches = if (type == "tv") {
                    TmdbClient.api.searchTv(tmdbAuthorizationHeader(), query, language).results.mapNotNull { result ->
                        val id = result.id ?: return@mapNotNull null
                        StudioMatchCandidate(
                            id, result.name ?: "Untitled", result.first_air_date?.take(4)?.toIntOrNull(),
                            result.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" },
                            result.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
                            result.overview, result.vote_average, "tv"
                        )
                    }
                } else {
                    val year = extractYearHint(rawQuery)
                    val first = TmdbClient.api.searchMovie(tmdbAuthorizationHeader(), query, year, language).results
                    val results = if (first.isEmpty() && year != null) {
                        TmdbClient.api.searchMovie(tmdbAuthorizationHeader(), query, null, language).results
                    } else first
                    results.mapNotNull { result ->
                        val id = result.id ?: return@mapNotNull null
                        StudioMatchCandidate(
                            id, result.title ?: "Untitled", result.release_date?.take(4)?.toIntOrNull(),
                            result.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" },
                            result.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
                            result.overview, result.vote_average, "movie"
                        )
                    }
                }
                ArtworkStudioResult.Success(matches)
            } catch (e: Exception) {
                ArtworkStudioResult.Failure(providerError("TMDB", e).message)
            }
        }

    suspend fun applyMatch(
        context: Context,
        items: List<VideoWithMetadata>,
        candidate: StudioMatchCandidate
    ): ArtworkStudioResult<List<VideoWithMetadata>> = withContext(Dispatchers.IO) {
        try {
            if (candidate.type == "movie") {
                val item = items.first()
                val updated = applyRematch(
                    context,
                    item,
                    MatchCandidate(
                        candidate.tmdbId, candidate.title, candidate.year,
                        candidate.posterUrl?.substringAfter("https://image.tmdb.org/t/p/w342"),
                        candidate.backdropUrl?.substringAfter("https://image.tmdb.org/t/p/w780"),
                        candidate.overview, candidate.rating
                    )
                )
                ArtworkStudioResult.Success(listOf(updated))
            } else {
                val language = loadMetadataLanguage(context)
                val extra = fetchTmdbExtraDetails(candidate.tmdbId, "tv", language)
                val fanart = if (candidate.posterUrl == null || candidate.backdropUrl == null) {
                    fetchFanartArtwork(candidate.tmdbId, "tv", metadataArtworkLanguage(context))
                } else null
                val updated = items.map { item ->
                    clearManualArtworkChoices(context, item.video.path)
                    item.copy(
                        title = candidate.title,
                        posterUrl = candidate.posterUrl ?: fanart?.posterUrl,
                        backdropUrl = candidate.backdropUrl ?: fanart?.backdropUrl,
                        overview = candidate.overview ?: item.overview,
                        rating = candidate.rating ?: item.rating,
                        tmdbId = candidate.tmdbId,
                        type = "tv",
                        genres = extra?.genres ?: emptyList(),
                        director = extra?.director,
                        curatedCollections = extra?.curatedCollections ?: emptyList(),
                        cast = extra?.cast ?: emptyList()
                    ).also { saveCachedVideoMetadata(context, item.video.path, it) }
                }
                ArtworkStudioResult.Success(updated)
            }
        } catch (e: Exception) {
            ArtworkStudioResult.Failure(e.message ?: "The new match could not be applied")
        }
    }

    suspend fun applyChoice(
        context: Context,
        items: List<VideoWithMetadata>,
        kind: ArtworkKind,
        url: String?
    ): ArtworkStudioResult<List<VideoWithMetadata>> = try {
        val updated = if (items.size > 1 || items.first().type == "tv") {
            applyTvArtworkChoice(context, items, kind, url)
        } else listOf(applyArtworkChoice(context, items.first(), kind, url))
        ArtworkStudioResult.Success(updated)
    } catch (e: Exception) {
        ArtworkStudioResult.Failure(e.message ?: "Artwork could not be applied")
    }

    suspend fun refresh(context: Context, items: List<VideoWithMetadata>): ArtworkStudioResult<List<VideoWithMetadata>> =
        try {
            val updated = if (items.size > 1 || items.first().type == "tv") refreshTvArtwork(context, items)
            else listOf(refreshArtwork(context, items.first()))
            ArtworkStudioResult.Success(updated)
        } catch (e: Exception) {
            ArtworkStudioResult.Failure(e.message ?: "Artwork could not be refreshed")
        }
}
