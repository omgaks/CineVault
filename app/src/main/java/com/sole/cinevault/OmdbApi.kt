package com.sole.cinevault

import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApi {

    @GET("/")
    suspend fun getRatings(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String
    ): OmdbResponse
}

data class OmdbResponse(
    val Title: String? = null,
    val Year: String? = null,
    val imdbRating: String? = null,
    val Ratings: List<OmdbRating>? = null,
    val Response: String? = null,
    val Error: String? = null
)

data class OmdbRating(
    val Source: String? = null,
    val Value: String? = null
)