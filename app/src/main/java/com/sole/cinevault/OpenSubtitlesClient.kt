package com.sole.cinevault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object OpenSubtitlesClient {

    // Paste your OpenSubtitles API key here.
    // Keep the quotes.
    private const val API_KEY = "BEkvR8mckzRqXsNNCd53o3hkK806U7jn"

    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "CineVault v1.0"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadBestEnglishSubtitle(
        context: Context,
        videoPath: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cleanName = videoPath
                .substringAfterLast("/")
                .substringAfterLast("\\")
                .substringBeforeLast(".")
                .replace(".", " ")
                .replace("_", " ")
                .replace("-", " ")
                .trim()

            if (cleanName.isBlank()) return@withContext null

            val query = URLEncoder.encode(cleanName, "UTF-8")
            val searchUrl = "$BASE_URL/subtitles?query=$query&languages=en&order_by=download_count&order_direction=desc"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .get()
                .addHeader("Api-Key", API_KEY)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()

            val searchResponse = httpClient.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string().orEmpty()

            if (!searchResponse.isSuccessful || searchBody.isBlank()) {
                return@withContext null
            }

            val searchJson = JSONObject(searchBody)
            val dataArray = searchJson.optJSONArray("data") ?: return@withContext null

            if (dataArray.length() == 0) return@withContext null

            var selectedFileId: Int? = null

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val files = attributes.optJSONArray("files") ?: continue

                if (files.length() > 0) {
                    val fileObj = files.optJSONObject(0) ?: continue
                    val fileId = fileObj.optInt("file_id", -1)
                    if (fileId > 0) {
                        selectedFileId = fileId
                        break
                    }
                }
            }

            val fileId = selectedFileId ?: return@withContext null

            val payload = JSONObject()
                .put("file_id", fileId)
                .put("sub_format", "srt")
                .toString()

            val downloadRequest = Request.Builder()
                .url("$BASE_URL/download")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Api-Key", API_KEY)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val downloadResponse = httpClient.newCall(downloadRequest).execute()
            val downloadBody = downloadResponse.body?.string().orEmpty()

            if (!downloadResponse.isSuccessful || downloadBody.isBlank()) {
                return@withContext null
            }

            val downloadJson = JSONObject(downloadBody)
            val subtitleLink = downloadJson.optString("link", "")

            if (subtitleLink.isBlank()) return@withContext null

            val srtRequest = Request.Builder()
                .url(subtitleLink)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val srtResponse = httpClient.newCall(srtRequest).execute()
            val srtText = srtResponse.body?.string().orEmpty()

            if (!srtResponse.isSuccessful || srtText.isBlank()) {
                return@withContext null
            }

            val subtitleDir = File(context.cacheDir, "subtitles")
            if (!subtitleDir.exists()) subtitleDir.mkdirs()

            val safeFileName = cleanName
                .replace(Regex("[^A-Za-z0-9._ -]"), "")
                .take(80)
                .ifBlank { "subtitle" }

            val subtitleFile = File(subtitleDir, "$safeFileName.en.srt")
            subtitleFile.writeText(srtText, Charsets.UTF_8)

            Uri.fromFile(subtitleFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
