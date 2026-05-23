package com.sole.cinevault

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OpenSubApi {

    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Query("query") query: String,
        @Query("languages") languages: String = "en"
    ): SubtitleSearchResponse

    @POST("download")
    suspend fun downloadSubtitle(
        @Header("Api-Key") apiKey: String,
        @Body request: SubtitleDownloadRequest
    ): SubtitleDownloadResponse
}