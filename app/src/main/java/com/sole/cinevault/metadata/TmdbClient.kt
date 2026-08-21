package com.sole.cinevault.metadata

import com.sole.cinevault.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun tmdbAuthorizationHeader(): String {
    val token = BuildConfig.TMDB_TOKEN.trim()
    return when {
        token.isBlank() -> ""
        token.startsWith("Bearer ", ignoreCase = true) -> token
        else -> "Bearer $token"
    }
}

object TmdbClient {

    private val tmdbHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val raw = BuildConfig.TMDB_TOKEN.trim()
            val original = chain.request()
            val request = original.newBuilder().removeHeader("Authorization")
            when {
                raw.isBlank() -> Unit
                raw.matches(Regex("^[A-Fa-f0-9]{32}$")) -> {
                    request.url(original.url.newBuilder().setQueryParameter("api_key", raw).build())
                }
                raw.startsWith("Bearer ", ignoreCase = true) -> request.header("Authorization", raw)
                else -> request.header("Authorization", "Bearer $raw")
            }
            chain.proceed(request.build())
        }
        .build()

    private val tmdbRetrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(tmdbHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: TmdbApi = tmdbRetrofit.create(TmdbApi::class.java)

    private val omdbRetrofit = Retrofit.Builder()
        .baseUrl("https://www.omdbapi.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val omdbApi: OmdbApi = omdbRetrofit.create(OmdbApi::class.java)
}
