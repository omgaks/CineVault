package com.sole.cinevault

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

// Diagnostic result type — Ash builds from a tablet with no computer, so
// Logcat isn't a usable diagnostic surface for him. This carries the real
// failure reason (HTTP code + the API's own error message) all the way back
// to the player screen so it can be shown directly on-screen instead.
sealed class SubtitleDownloadResult {
    data class Success(val uri: Uri) : SubtitleDownloadResult()
    data class SearchHttpError(val code: Int, val detail: String) : SubtitleDownloadResult()
    data class NoResults(val triedTerms: List<String>) : SubtitleDownloadResult()
    data class DownloadHttpError(val code: Int, val detail: String) : SubtitleDownloadResult()
    object QuotaExhausted : SubtitleDownloadResult()
    data class SrtFetchError(val code: Int) : SubtitleDownloadResult()
    data class UnexpectedError(val detail: String) : SubtitleDownloadResult()

    // Short, on-screen-friendly summary of what went wrong.
    fun summary(): String = when (this) {
        is Success -> "Subtitle loaded"
        is SearchHttpError -> "Search error $code: ${detail.take(70)}"
        is NoResults -> "No subtitle found"
        is DownloadHttpError -> "Download blocked ($code): ${detail.take(70)}"
        is QuotaExhausted -> "Daily subtitle quota used up — try again tomorrow"
        is SrtFetchError -> "Subtitle file fetch failed ($code)"
        is UnexpectedError -> "Subtitle error: ${detail.take(70)}"
    }
}

object OpenSubtitlesClient {

    private val API_KEY: String get() = BuildConfig.OPENSUB_API_KEY
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "CineVault v1.0"
    private const val TAG = "OpenSubtitlesClient"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Backward-compatible convenience wrapper — returns just the Uri like
    // before, for any call site that doesn't care about the diagnostic detail.
    suspend fun downloadBestEnglishSubtitle(context: Context, videoPath: String): Uri? {
        val result = downloadBestEnglishSubtitleDetailed(context, videoPath)
        return (result as? SubtitleDownloadResult.Success)?.uri
    }

    // Fast, network-free check for a subtitle already downloaded for this
    // exact video in a previous session. Lets the player attach it
    // immediately on load instead of always kicking off a search first.
    fun findCachedSubtitle(context: Context, videoPath: String): Uri? {
        val file = subtitleCacheFile(context, videoPath)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file) else null
    }

    // Persistent (not OS-clearable) storage, keyed by a hash of the exact
    // video path rather than the cleaned search name — decouples caching
    // from the title-cleaning logic (which can change over time) and ties
    // the cached file unambiguously to one specific video on disk.
    private fun subtitleCacheDir(context: Context): File {
        val dir = File(context.filesDir, "subtitles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // FIX: the cache key is hashed from this normalized form of the path
    // now, not the raw string. Previously, if the SAME physical file was
    // ever represented by two cosmetically-different path strings across
    // sessions/rescans — most commonly a URL-encoded space ("%20") in one
    // scan vs a literal space in another, or a backslash vs forward slash
    // separator — the MD5 hash came out completely different each time,
    // so findCachedSubtitle() reported a cache miss for a subtitle that was
    // actually sitting right there on disk under a different-looking key.
    // Every path now goes through the same normalization (URL-decoded,
    // separators unified, trimmed) before hashing, so cosmetic differences
    // in how a path happens to be written can no longer produce two
    // different cache entries for what's really the same file.
    private fun normalizedPathForCacheKey(videoPath: String): String {
        val decoded = try {
            java.net.URLDecoder.decode(videoPath, "UTF-8")
        } catch (_: Exception) {
            // Not actually URL-encoded, or contains a stray '%' that isn't a
            // valid escape — fall back to the original string rather than
            // let decoding failure break caching entirely.
            videoPath
        }
        return decoded.replace('\\', '/').trim()
    }

    private fun subtitleCacheKey(videoPath: String): String {
        val normalized = normalizedPathForCacheKey(videoPath)
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5").digest(normalized.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // MD5 is always available on Android in practice, but fall back
            // to a sanitized path rather than crash if it somehow isn't.
            normalized.replace(Regex("[^A-Za-z0-9]"), "_").takeLast(80)
        }
    }

    private fun subtitleCacheFile(context: Context, videoPath: String): File =
        File(subtitleCacheDir(context), "${subtitleCacheKey(videoPath)}.en.srt")

    suspend fun downloadBestEnglishSubtitleDetailed(
        context: Context,
        videoPath: String
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            // Cache check FIRST — if this exact video already has a
            // downloaded subtitle sitting on disk (even from a previous app
            // session), reuse it instantly instead of re-searching/
            // re-downloading every time the same movie is opened.
            findCachedSubtitle(context, videoPath)?.let { cachedUri ->
                Log.d(TAG, "Subtitle cache hit for $videoPath")
                return@withContext SubtitleDownloadResult.Success(cachedUri)
            }

            val cleanName = cleanMovieName(videoPath)
            Log.d(TAG, "Clean search name: $cleanName")

            if (cleanName.isBlank()) {
                return@withContext SubtitleDownloadResult.UnexpectedError("Could not derive a search name from the file name")
            }

            val attempts = buildSearchAttempts(cleanName)
            Log.d(TAG, "Search attempts (in order): $attempts")

            var fileId: Int? = null
            var lastHttpError: Pair<Int, String>? = null
            for (attempt in attempts) {
                when (val r = searchFileId(attempt)) {
                    is SearchAttemptResult.Found -> { fileId = r.fileId; break }
                    is SearchAttemptResult.HttpError -> lastHttpError = r.code to r.bodyPreview
                    SearchAttemptResult.NoResults -> {}
                }
            }

            if (fileId == null) {
                if (lastHttpError != null) {
                    return@withContext SubtitleDownloadResult.SearchHttpError(lastHttpError.first, lastHttpError.second)
                }
                return@withContext SubtitleDownloadResult.NoResults(attempts)
            }

            val linkResult = getDownloadLink(fileId)
            val subtitleLink = when (linkResult) {
                is DownloadLinkResult.Found -> linkResult.link
                is DownloadLinkResult.HttpError -> return@withContext SubtitleDownloadResult.DownloadHttpError(linkResult.code, linkResult.bodyPreview)
                DownloadLinkResult.QuotaExhausted -> return@withContext SubtitleDownloadResult.QuotaExhausted
                DownloadLinkResult.EmptyLink -> return@withContext SubtitleDownloadResult.DownloadHttpError(0, "API returned no download link")
            }

            val srtResult = downloadSrt(subtitleLink)
            val srtText = when (srtResult) {
                is SrtResult.Found -> srtResult.text
                is SrtResult.HttpError -> return@withContext SubtitleDownloadResult.SrtFetchError(srtResult.code)
            }

            val subtitleFile = subtitleCacheFile(context, videoPath)
            subtitleFile.writeText(srtText, Charsets.UTF_8)

            Log.d(TAG, "Subtitle saved: ${subtitleFile.absolutePath}")
            SubtitleDownloadResult.Success(Uri.fromFile(subtitleFile))

        } catch (e: Exception) {
            Log.e(TAG, "Subtitle error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    // Progressive fallback: full cleaned name -> name without year -> first 4
    // words -> first 2 words, de-duplicated.
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

    private sealed class SearchAttemptResult {
        data class Found(val fileId: Int) : SearchAttemptResult()
        data class HttpError(val code: Int, val bodyPreview: String) : SearchAttemptResult()
        object NoResults : SearchAttemptResult()
    }

    private fun searchFileId(searchName: String): SearchAttemptResult {
        if (searchName.isBlank()) return SearchAttemptResult.NoResults

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
                Log.w(TAG, "Search request failed. code=${response.code} body=${body.take(500)}")
                val detail = extractApiMessage(body) ?: "HTTP ${response.code}"
                return SearchAttemptResult.HttpError(response.code, detail)
            }

            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return SearchAttemptResult.NoResults
            if (dataArray.length() == 0) return SearchAttemptResult.NoResults

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val files = attributes.optJSONArray("files") ?: continue

                if (files.length() > 0) {
                    val fileObj = files.optJSONObject(0) ?: continue
                    val fileId = fileObj.optInt("file_id", -1)
                    if (fileId > 0) return SearchAttemptResult.Found(fileId)
                }
            }
        }

        return SearchAttemptResult.NoResults
    }

    private sealed class DownloadLinkResult {
        data class Found(val link: String) : DownloadLinkResult()
        data class HttpError(val code: Int, val bodyPreview: String) : DownloadLinkResult()
        object QuotaExhausted : DownloadLinkResult()
        object EmptyLink : DownloadLinkResult()
    }

    private fun getDownloadLink(fileId: Int): DownloadLinkResult {
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
                Log.w(TAG, "Download-link request failed. code=${response.code} body=${body.take(500)}")
                val detail = extractApiMessage(body) ?: "HTTP ${response.code}"
                return DownloadLinkResult.HttpError(response.code, detail)
            }

            val json = JSONObject(body)
            val remaining = json.optInt("remaining", -1)
            if (remaining == 0) {
                Log.w(TAG, "OpenSubtitles daily download quota is exhausted (remaining=0).")
                return DownloadLinkResult.QuotaExhausted
            }

            val link = json.optString("link", "")
            return if (link.isNotBlank()) DownloadLinkResult.Found(link) else DownloadLinkResult.EmptyLink
        }
    }

    private sealed class SrtResult {
        data class Found(val text: String) : SrtResult()
        data class HttpError(val code: Int) : SrtResult()
    }

    private fun downloadSrt(link: String): SrtResult {
        val request = Request.Builder()
            .url(link)
            .get()
            .addHeader("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.d(TAG, "SRT response code: ${response.code}")

            if (!response.isSuccessful || text.isBlank()) {
                Log.w(TAG, "SRT download failed. code=${response.code}")
                return SrtResult.HttpError(response.code)
            }
            return SrtResult.Found(text)
        }
    }

    // OpenSubtitles error responses typically look like {"message": "..."} or
    // {"errors": ["..."]} — pull out whichever is present so the on-screen
    // summary shows the API's actual words, not just a bare status code.
    private fun extractApiMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            json.optString("message", "").takeIf { it.isNotBlank() }
                ?: json.optJSONArray("errors")?.optString(0)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            body.take(80)
        }
    }
}
