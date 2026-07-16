package com.sole.cinevault

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Header("Authorization") bearerToken: String,
        @Query("query") query: String
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
    // append_to_response=credits pulls cast+crew into the SAME response as
    // genres/collection, instead of needing a second network call.
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Header("Authorization") bearerToken: String,
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "credits"
    ): TmdbMovieDetails

    @GET("tv/{series_id}")
    suspend fun getTvDetails(
        @Header("Authorization") bearerToken: String,
        @Path("series_id") seriesId: Int,
        @Query("append_to_response") appendToResponse: String = "credits"
    ): TmdbTvDetails
}

data class TmdbExternalIds(
    val imdb_id: String?
)
