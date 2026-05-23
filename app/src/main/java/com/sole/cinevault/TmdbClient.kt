package com.sole.cinevault

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmdbClient {

    const val BEARER =
        "Bearer YOUR_TMDB_BEARER_TOKEN"

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: TmdbApi = retrofit.create(TmdbApi::class.java)
}