package com.sole.cinevault

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OmdbClient {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.omdbapi.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: OmdbApi = retrofit.create(OmdbApi::class.java)
}