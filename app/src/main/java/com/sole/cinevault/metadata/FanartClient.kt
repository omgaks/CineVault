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

enum class ArtworkKind {
    POSTER,
    BACKDROP
}

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

private fun List<FanartImage>.bestArtworkUrl(
    preferredLanguage: String
): String? {
    return asSequence()
        .filter { it.url?.startsWith("https://") == true }
        .sortedWith(
            compareByDescending<FanartImage> {
                when {
                    it.lang.equals(preferredLanguage, ignoreCase = true) -> 3
                    it.lang.equals("en", ignoreCase = true) -> 2
                    it.lang.isNullOrBlank() || it.lang == "00" -> 1
                    else -> 0
                }
            }.thenByDescending {
                it.likes?.toIntOrNull() ?: 0
            }
        )
        .mapNotNull { it.url }
        .firstOrNull()
}

private fun List<FanartImage>.toArtworkOptions(
    kind: ArtworkKind,
    preferredLanguage: String
): List<ArtworkOption> {
    return asSequence()
        .filter { it.url?.startsWith("https://") == true }
        .mapNotNull { image ->
            val imageUrl = image.url ?: return@mapNotNull null

            ArtworkOption(
                url = imageUrl,
                source = "Fanart.tv",
                kind = kind,
                language = image.lang,
                score = (image.likes?.toDoubleOrNull() ?: 0.0) +
                    when {
                        image.lang.equals(
                            preferredLanguage,
                            ignoreCase = true
                        ) -> 3_000.0

                        image.lang.equals(
                            "en",
                            ignoreCase = true
                        ) -> 2_000.0

                        image.lang.isNullOrBlank() ||
                            image.lang == "00" -> 1_000.0

                        else -> 0.0
                    }
            )
        }
        .sortedWith(
            compareByDescending<ArtworkOption> {
                when {
                    it.language.equals(
                        preferredLanguage,
                        ignoreCase = true
                    ) -> 3

                    it.language.equals(
                        "en",
                        ignoreCase = true
                    ) -> 2

                    it.language.isNullOrBlank() ||
                        it.language == "00" -> 1

                    else -> 0
                }
            }.thenByDescending {
                it.score
            }
        )
        .toList()
}

suspend fun fetchFanartArtworkOptions(
    tmdbId: Int,
    type: String,
    preferredLanguage: String = "en"
): List<ArtworkOption> =
    withContext(Dispatchers.IO) {
        try {
            fetchFanartArtworkOptionsStrict(
                tmdbId = tmdbId,
                type = type,
                preferredLanguage = preferredLanguage
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

suspend fun fetchFanartArtworkOptionsStrict(
    tmdbId: Int,
    type: String,
    preferredLanguage: String = "en"
): List<ArtworkOption> =
    withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.FANART_API_KEY.trim()

        require(apiKey.isNotBlank()) {
            "Fanart.tv API key is not configured"
        }
        require(tmdbId > 0) {
            "A valid TMDB match is required"
        }

        if (type == "tv") {
            val tvdbId = TmdbClient.api
                .getTvExternalIds(
                    tmdbAuthorizationHeader(),
                    tmdbId
                )
                .tvdb_id
                ?: throw IllegalStateException(
                    "No TheTVDB ID is available for this show"
                )

            val response = FanartClient.api.getTvArtwork(
                tvdbId,
                apiKey
            )

            response.tvposter.orEmpty().toArtworkOptions(
                ArtworkKind.POSTER,
                preferredLanguage
            ) +
                (
                    response.showbackground.orEmpty() +
                        response.show4kbackground.orEmpty()
                    ).toArtworkOptions(
                    ArtworkKind.BACKDROP,
                    preferredLanguage
                )
        } else {
            val response = FanartClient.api.getMovieArtwork(
                tmdbId,
                apiKey
            )

            response.movieposter.orEmpty().toArtworkOptions(
                ArtworkKind.POSTER,
                preferredLanguage
            ) +
                (
                    response.moviebackground.orEmpty() +
                        response.movie4kbackground.orEmpty()
                    ).toArtworkOptions(
                    ArtworkKind.BACKDROP,
                    preferredLanguage
                )
        }
    }

/**
 * Returns Fanart.tv images only when TMDB did not provide them.
 * Movie lookup uses the TMDB ID directly. Fanart's TV endpoint
 * uses the corresponding TheTVDB ID obtained from TMDB.
 */
suspend fun fetchFanartArtwork(
    tmdbId: Int,
    type: String,
    preferredLanguage: String = "en"
): FanartArtwork? =
    withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.FANART_API_KEY.trim()

        if (apiKey.isBlank() || tmdbId <= 0) {
            return@withContext null
        }

        try {
            if (type == "tv") {
                val tvdbId = TmdbClient.api
                    .getTvExternalIds(
                        tmdbAuthorizationHeader(),
                        tmdbId
                    )
                    .tvdb_id
                    ?: return@withContext null

                val response = FanartClient.api.getTvArtwork(
                    tvdbId,
                    apiKey
                )

                FanartArtwork(
                    posterUrl = response.tvposter.orEmpty().bestArtworkUrl(
                        preferredLanguage
                    ),
                    backdropUrl =
                        response.showbackground.orEmpty().bestArtworkUrl(
                            preferredLanguage
                        )
                            ?: response.show4kbackground.orEmpty().bestArtworkUrl(
                                preferredLanguage
                            )
                )
            } else {
                val response = FanartClient.api.getMovieArtwork(
                    tmdbId,
                    apiKey
                )

                FanartArtwork(
                    posterUrl = response.movieposter.orEmpty().bestArtworkUrl(
                        preferredLanguage
                    ),
                    backdropUrl =
                        response.moviebackground.orEmpty().bestArtworkUrl(
                            preferredLanguage
                        )
                            ?: response.movie4kbackground.orEmpty().bestArtworkUrl(
                                preferredLanguage
                            )
                )
            }
        } catch (_: Exception) {
            null
        }
    }
