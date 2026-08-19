package com.sole.cinevault.metadata

import com.sole.cinevault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private interface FanartApi {
    @GET("v3/movies/{tmdbId}")
    suspend fun getMovieArtwork(
        @Path("tmdbId") tmdbId: Int,
        @Query("api_key") apiKey: String
    ): FanartMovieResponse

    @GET("v3/tv/{tvdbId}")
    suspend fun getTvArtwork(
        @Path("tvdbId") tvdbId: Int,
        @Query("api_key") apiKey: String
    ): FanartTvResponse
}

private data class FanartImage(
    val id: String? = null,
    val url: String? = null,
    val lang: String? = null,
    val likes: String? = null
)

private data class FanartMovieResponse(
    val movieposter: List<FanartImage> = emptyList(),
    val moviebackground: List<FanartImage> = emptyList(),
    val movie4kbackground: List<FanartImage> = emptyList()
)

private data class FanartTvResponse(
    val tvposter: List<FanartImage> = emptyList(),
    val showbackground: List<FanartImage> = emptyList(),
    val show4kbackground: List<FanartImage> = emptyList()
)

data class FanartArtwork(
    val posterUrl: String?,
    val backdropUrl: String?
)

enum class ArtworkKind { POSTER, BACKDROP }

data class ArtworkOption(
    val url: String,
    val source: String,
    val kind: ArtworkKind,
    val language: String? = null,
    val score: Double = 0.0
)

private object FanartClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://webservice.fanart.tv/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: FanartApi = retrofit.create(FanartApi::class.java)
}

private fun List<FanartImage>.bestArtworkUrl(): String? {
    return asSequence()
        .filter { it.url?.startsWith("https://") == true }
        .sortedWith(
            compareByDescending<FanartImage> {
                when {
                    it.lang.equals("en", ignoreCase = true) -> 2
                    it.lang.isNullOrBlank() || it.lang == "00" -> 1
                    else -> 0
                }
            }.thenByDescending { it.likes?.toIntOrNull() ?: 0 }
        )
        .mapNotNull { it.url }
        .firstOrNull()
}

private fun List<FanartImage>.toArtworkOptions(kind: ArtworkKind): List<ArtworkOption> {
    return asSequence()
        .filter { it.url?.startsWith("https://") == true }
        .mapNotNull { image ->
            val url = image.url ?: return@mapNotNull null
            ArtworkOption(
                url = url,
                source = "Fanart.tv",
                kind = kind,
                language = image.lang,
                score = image.likes?.toDoubleOrNull() ?: 0.0
            )
        }
        .sortedWith(
            compareByDescending<ArtworkOption> {
                when {
                    it.language.equals("en", ignoreCase = true) -> 2
                    it.language.isNullOrBlank() || it.language == "00" -> 1
                    else -> 0
                }
            }.thenByDescending { it.score }
        )
        .toList()
}

suspend fun fetchFanartArtworkOptions(tmdbId: Int, type: String): List<ArtworkOption> =
    withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.FANART_API_KEY
        if (apiKey.isBlank() || tmdbId <= 0) return@withContext emptyList()
        try {
            if (type == "tv") {
                val tvdbId = TmdbClient.api
                    .getTvExternalIds(BuildConfig.TMDB_TOKEN, tmdbId)
                    .tvdb_id
                    ?: return@withContext emptyList()
                val response = FanartClient.api.getTvArtwork(tvdbId, apiKey)
                response.tvposter.toArtworkOptions(ArtworkKind.POSTER) +
                        (response.showbackground + response.show4kbackground)
                            .toArtworkOptions(ArtworkKind.BACKDROP)
            } else {
                val response = FanartClient.api.getMovieArtwork(tmdbId, apiKey)
                response.movieposter.toArtworkOptions(ArtworkKind.POSTER) +
                        (response.moviebackground + response.movie4kbackground)
                            .toArtworkOptions(ArtworkKind.BACKDROP)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

/**
 * Returns Fanart.tv images only when TMDB did not provide them. Movie lookup
 * uses the TMDB id directly; Fanart's TV endpoint uses the corresponding
 * TheTVDB id, which TMDB exposes through its external-ids endpoint.
 */
suspend fun fetchFanartArtwork(tmdbId: Int, type: String): FanartArtwork? =
    withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.FANART_API_KEY
        if (apiKey.isBlank() || tmdbId <= 0) return@withContext null

        try {
            if (type == "tv") {
                val tvdbId = TmdbClient.api
                    .getTvExternalIds(BuildConfig.TMDB_TOKEN, tmdbId)
                    .tvdb_id
                    ?: return@withContext null
                val response = FanartClient.api.getTvArtwork(tvdbId, apiKey)
                FanartArtwork(
                    posterUrl = response.tvposter.bestArtworkUrl(),
                    backdropUrl = response.showbackground.bestArtworkUrl()
                        ?: response.show4kbackground.bestArtworkUrl()
                )
            } else {
                val response = FanartClient.api.getMovieArtwork(tmdbId, apiKey)
                FanartArtwork(
                    posterUrl = response.movieposter.bestArtworkUrl(),
                    backdropUrl = response.moviebackground.bestArtworkUrl()
                        ?: response.movie4kbackground.bestArtworkUrl()
                )
            }
        } catch (_: Exception) {
            null
        }
    }
