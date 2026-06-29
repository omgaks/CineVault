package com.sole.cinevault

import okhttp3.FormBody
import android.content.Context
import android.net.Uri
import android.util.Log
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

    private val API_KEY: String get() = BuildConfig.OPENSUB_API_KEY
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "CineVault v1.0"
    private const val TAG = "OpenSubtitlesClient"

    // USERNAME and PASSWORD removed — OpenSubtitles free tier works without login for search
    // If you need login, store credentials in local.properties and add to build.gradle.kts

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadBestEnglishSubtitle(
        context: Context,
        videoPath: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cleanName = cleanMovieName(videoPath)
            Log.d(TAG, "Clean search name: $cleanName")

            if (cleanName.isBlank()) return@withContext null

            val fileId = searchFileId(cleanName)
                ?: searchFileId(cleanName.substringBeforeLast(" "))
                ?: return@withContext null

            val subtitleLink = getDownloadLink(fileId) ?: return@withContext null
            val srtText = downloadSrt(subtitleLink) ?: return@withContext null

            val subtitleDir = File(context.cacheDir, "subtitles")
            if (!subtitleDir.exists()) subtitleDir.mkdirs()

            val safeFileName = cleanName
                .replace(Regex("[^A-Za-z0-9._ -]"), "")
                .take(80)
                .ifBlank { "subtitle" }

            val subtitleFile = File(subtitleDir, "$safeFileName.en.srt")
            subtitleFile.writeText(srtText, Charsets.UTF_8)

            Log.d(TAG, "Subtitle saved: ${subtitleFile.absolutePath}")
            Uri.fromFile(subtitleFile)

        } catch (e: Exception) {
            Log.e(TAG, "Subtitle error: ${e.message}", e)
            null
        }
    }

    private fun cleanMovieName(videoPath: String): String {
        var name = videoPath
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .substringBeforeLast(".")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\[.*?]"), " ")
            .replace(Regex("\\(.*?\\)"), " ")

        name = name.replace(
            Regex(
                "\\b(2160p|1080p|720p|480p|4k|uhd|hdr|dv|dolby|vision|bluray|blu ray|brrip|webdl|web dl|webrip|web rip|hdrip|x264|x265|h264|h265|hevc|10bit|aac|aac5|ddp|dts|atmos|5 1|7 1|yts|rarbg|eztv|tgx|repack|proper|extended|remux|multi|dual|audio|hindi|english|eng|ita|amzn|nf|web|mkv|mp4|avi)\\b",
                RegexOption.IGNORE_CASE
            ),
            " "
        )

        name = name.replace(Regex("\\s+"), " ").trim()

        val yearMatch = Regex("\\b(19|20)\\d{2}\\b").find(name)
        if (yearMatch != null) {
            name = name.substring(0, yearMatch.range.last + 1)
        }

        return name.replace(Regex("\\s+"), " ").trim()
    }

    private fun searchFileId(searchName: String): Int? {
        if (searchName.isBlank()) return null

        val query = URLEncoder.encode(searchName, "UTF-8")
        val searchUrl =
            "$BASE_URL/subtitles?query=$query&languages=en&order_by=download_count&order_direction=desc"

        val request = Request.Builder()
            .url(searchUrl)
            .get()
            .addHeader("Api-Key", API_KEY)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "Search response code: ${response.code}")

            if (!response.isSuccessful || body.isBlank()) return null

            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return null
            if (dataArray.length() == 0) return null

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val files = attributes.optJSONArray("files") ?: continue

                if (files.length() > 0) {
                    val fileObj = files.optJSONObject(0) ?: continue
                    val fileId = fileObj.optInt("file_id", -1)
                    if (fileId > 0) return fileId
                }
            }
        }

        return null
    }

    private fun getDownloadLink(fileId: Int): String? {
        val payload = JSONObject()
            .put("file_id", fileId)
            .put("sub_format", "srt")
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/download")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Api-Key", API_KEY)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "Download response code: ${response.code}")

            if (!response.isSuccessful || body.isBlank()) return null

            val json = JSONObject(body)
            return json.optString("link", "").takeIf { it.isNotBlank() }
        }
    }

    private fun downloadSrt(link: String): String? {
        val request = Request.Builder()
            .url(link)
            .get()
            .addHeader("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d(TAG, "SRT response code: ${response.code}")

            if (!response.isSuccessful || text.isBlank()) return null
            return text
        }
    }
}
