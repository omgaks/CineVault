package com.sole.cinevault

import retrofit2.http.GET
import retrofit2.http.Query

data class OmdbResponse(
    val Title: String?,
    val Year: String?,
    val imdbRating: String?,
    val Ratings: List<OmdbRating>?,
    val Response: String?,
    val Error: String?
)

data class OmdbRating(
    val Source: String,
    val Value: String
)

interface OmdbApi {
    @GET("/")
    suspend fun getRatings(
        @Query("t") title: String,
        @Query("y") year: String? = null,
        @Query("type") type: String? = null,
        @Query("apikey") apiKey: String
    ): OmdbResponse
}