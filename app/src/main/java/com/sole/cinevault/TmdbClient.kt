package com.sole.cinevault

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmdbClient {

    private val tmdbRetrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: TmdbApi = tmdbRetrofit.create(TmdbApi::class.java)

    private val omdbRetrofit = Retrofit.Builder()
        .baseUrl("https://www.omdbapi.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val omdbApi: OmdbApi = omdbRetrofit.create(OmdbApi::class.java)
}