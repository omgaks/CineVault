package com.sole.cinevault.metadata

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Header("Authorization") bearerToken: String,
        @Query("query") query: String,
        // FIX: previously no way to filter by year at all — every caller
        // had to embed a year (if any) directly into the free-text query
        // string itself, which TMDB's search doesn't handle well (a year
        // isn't part of a movie's actual title, and mixing it into the
        // text query can suppress otherwise-good matches rather than
        // narrow them). TMDB's search endpoint has always supported year
        // filtering as its OWN separate parameter — this just actually
        // uses it. Optional/nullable so existing callers that don't pass
        // a year keep behaving exactly as before.
        @Query("primary_release_year") primaryReleaseYear: String? = null
    ): TmdbMovieSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Header("Authorization") bearerToken: String,
        @Query("query") query: String
    ): TmdbTvSearchResponse

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Header("Authorization") bearerToken: String,
        @Path("movie_id") movieId: Int
    ): TmdbCreditsResponse

    @GET("tv/{series_id}/credits")
    suspend fun getTvCredits(
        @Header("Authorization") bearerToken: String,
        @Path("series_id") seriesId: Int
    ): TmdbCreditsResponse

    @GET("tv/{series_id}/season/{season_number}/episode/{episode_number}")
    suspend fun getEpisodeDetails(
        @Header("Authorization") bearerToken: String,
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int
    ): TmdbEpisode

    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(
        @Header("Authorization") bearerToken: String,
        @Path("movie_id") movieId: Int
    ): TmdbExternalIds

    @GET("tv/{series_id}/external_ids")
    suspend fun getTvExternalIds(
        @Header("Authorization") bearerToken: String,
        @Path("series_id") seriesId: Int
    ): TmdbExternalIds

    // ── Media intelligence additions ───────────────────────────────────────
    // append_to_response bundles credits AND keywords into the SAME
    // response as genres/collection — zero extra network round-trips.
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Header("Authorization") bearerToken: String,
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "credits,keywords"
    ): TmdbMovieDetails

    @GET("tv/{series_id}")
    suspend fun getTvDetails(
        @Header("Authorization") bearerToken: String,
        @Path("series_id") seriesId: Int,
        @Query("append_to_response") appendToResponse: String = "credits,keywords"
    ): TmdbTvDetails
}

data class TmdbExternalIds(
    val imdb_id: String?,
    val tvdb_id: Int? = null
)
