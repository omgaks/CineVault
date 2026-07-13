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

    // USERNAME and PASSWORD removed — OpenSubtitles free tier works without login for search.
    // NOTE: some API keys/tiers can search fine but get rejected specifically at the
    // /download step unless authenticated with a login token. If subtitles are never
    // found, check Logcat for "Search failed" vs "Download-link failed" — that tells you
    // which step is actually the problem, since both used to look identical from the UI.

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

            if (cleanName.isBlank()) {
                Log.w(TAG, "Cleaned name is blank — cannot search. Raw path: $videoPath")
                return@withContext null
            }

            // Progressive fallback chain: full cleaned name -> name without the
            // year -> first 4 words -> first 2 words. Each is only attempted if
            // it's actually different from the ones already tried, so a single-
            // word title (where the old .substringBeforeLast(" ") fallback was a
            // no-op) still gets a real second attempt.
            val attempts = buildSearchAttempts(cleanName)
            Log.d(TAG, "Search attempts (in order): $attempts")

            var fileId: Int? = null
            for (attempt in attempts) {
                fileId = searchFileId(attempt)
                if (fileId != null) {
                    Log.d(TAG, "Match found using search term: \"$attempt\"")
                    break
                }
            }
            if (fileId == null) {
                Log.w(TAG, "Search failed — no results for any of: $attempts")
                return@withContext null
            }

            val subtitleLink = getDownloadLink(fileId)
            if (subtitleLink == null) {
                Log.w(TAG, "Download-link failed for file_id=$fileId — search succeeded but /download rejected the request. This usually means the API key/tier requires login to download, or the daily download quota is used up. Check the response body logged above.")
                return@withContext null
            }

            val srtText = downloadSrt(subtitleLink)
            if (srtText == null) {
                Log.w(TAG, "SRT text download failed from link: $subtitleLink")
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

            Log.d(TAG, "Subtitle saved: ${subtitleFile.absolutePath}")
            Uri.fromFile(subtitleFile)

        } catch (e: Exception) {
            Log.e(TAG, "Subtitle error: ${e.message}", e)
            null
        }
    }

    // Builds a de-duplicated, progressively shorter list of search terms so a
    // failed exact-title search still gets meaningful retries instead of one
    // redundant repeat (the old fallback was a no-op for single-word titles).
    private fun buildSearchAttempts(cleanName: String): List<String> {
        val attempts = LinkedHashSet<String>()
        attempts.add(cleanName)

        val withoutYear = cleanName.replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim()
        if (withoutYear.isNotBlank()) attempts.add(withoutYear)

        val words = withoutYear.split(" ").filter { it.isNotBlank() }
        if (words.size > 4) attempts.add(words.take(4).joinToString(" "))
        if (words.size > 2) attempts.add(words.take(2).joinToString(" "))

        return attempts.toList()
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
            Log.d(TAG, "Search response code: ${response.code} for \"$searchName\"")

            if (!response.isSuccessful || body.isBlank()) {
                // The body here is the actual reason from OpenSubtitles (bad key,
                // rate limit, etc.) — this used to be swallowed entirely.
                Log.w(TAG, "Search request failed. code=${response.code} body=${body.take(500)}")
                return null
            }

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

            if (!response.isSuccessful || body.isBlank()) {
                // This is the response body that tells you WHY — e.g. OpenSubtitles
                // returns messages like "You must be Vip or login" or quota errors
                // in this body when the key/tier can't download without auth.
                Log.w(TAG, "Download-link request failed. code=${response.code} body=${body.take(500)}")
                return null
            }

            val json = JSONObject(body)
            val remaining = json.optInt("remaining", -1)
            if (remaining == 0) {
                Log.w(TAG, "OpenSubtitles daily download quota is exhausted (remaining=0). Downloads will keep failing until it resets — this is an account/key limit, not a code bug.")
            } else if (remaining > 0) {
                Log.d(TAG, "OpenSubtitles downloads remaining today: $remaining")
            }
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

            if (!response.isSuccessful || text.isBlank()) {
                Log.w(TAG, "SRT download failed. code=${response.code} bodyPreview=${text.take(200)}")
                return null
            }
            return text
        }
    }
}
