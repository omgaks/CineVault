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
    // FIX: Success now carries the ACTUAL language that was downloaded.
    // Previously the cache was hardcoded to a single ".en.srt" slot per
    // video regardless of what language was actually requested/found, and
    // every caller hardcoded the UI label to "English" — meaning a Hindi
    // or Samoan download would silently show as "English" in Tracks, a
    // second download in a different language would overwrite the first
    // one's cache file, and "remember last language" could never work
    // correctly. See subtitleCacheFile() below for the storage side of
    // this fix.
    data class Success(val uri: Uri, val language: String) : SubtitleDownloadResult()
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

// One row of a multi-result search — everything the "Subtitle Download
// Search" result-card UI needs is captured here directly off the API
// response, so the composable layer never has to re-parse JSON.
data class SubtitleSearchResult(
    val fileId: Int,
    val language: String,
    val release: String,
    val downloadCount: Int,
    val rating: Double,
    val hearingImpaired: Boolean,
    val forced: Boolean,
    val aiTranslated: Boolean,
    val machineTranslated: Boolean,
    val fromTrusted: Boolean,
    val fps: Double?
) {
    // Best-effort source tag pulled from the release string itself (the API
    // doesn't return a separate structured field for this) — used for the
    // "Blu-ray / WEB-DL / HDTV" badge.
    val sourceTag: String? by lazy {
        val r = release.lowercase()
        when {
            r.contains("bluray") || r.contains("blu-ray") || r.contains("bdrip") -> "Blu-ray"
            r.contains("web-dl") || r.contains("webdl") -> "WEB-DL"
            r.contains("webrip") -> "WEBRip"
            r.contains("hdtv") -> "HDTV"
            r.contains("dvdrip") -> "DVDRip"
            else -> null
        }
    }
}

sealed class SubtitleSearchListResult {
    data class Success(val results: List<SubtitleSearchResult>) : SubtitleSearchListResult()
    data class HttpError(val code: Int, val detail: String) : SubtitleSearchListResult()
    object NoResults : SubtitleSearchListResult()
}

// A single cached subtitle's identity — returned by findCachedSubtitle so
// callers know not just WHERE the file is but WHAT LANGUAGE it actually is,
// rather than assuming.
data class CachedSubtitle(val uri: Uri, val language: String)

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

    // FIX: now language-aware — checks each preferred language IN ORDER
    // and returns both the URI and which language actually hit, instead
    // of blindly checking a single hardcoded ".en.srt" slot regardless of
    // what the person's actual language preference was. Returns null only
    // if NONE of the preferred languages have a cached copy.
    fun findCachedSubtitle(context: Context, videoPath: String, preferredLanguages: List<String>): CachedSubtitle? {
        for (lang in preferredLanguages.ifEmpty { listOf("en") }) {
            val file = subtitleCacheFile(context, videoPath, lang)
            if (file.exists() && file.length() > 0) return CachedSubtitle(Uri.fromFile(file), lang)
        }
        return null
    }

    // Single-language convenience overload — for the few call sites that
    // only ever care about one specific language (e.g. dual subtitles
    // checking whether ITS OWN secondary language already has a cached
    // copy before re-downloading it).
    fun findCachedSubtitle(context: Context, videoPath: String, language: String): CachedSubtitle? {
        val file = subtitleCacheFile(context, videoPath, language)
        return if (file.exists() && file.length() > 0) CachedSubtitle(Uri.fromFile(file), language) else null
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

    // FIX: now takes the actual language as a parameter and folds it into
    // the filename — previously ALWAYS produced "<hash>.en.srt" no matter
    // what language was actually downloaded, meaning a second download in
    // a different language would silently overwrite the first, and Dual
    // Subtitles needed a completely separate secondaryLanguageCacheFile()
    // function just to avoid that collision. One function now; every
    // language gets its own slot by construction, so the collision this
    // was working around can't happen anymore. Public since VideoPlayer-
    // Screen.kt's dual-subtitle code calls this directly (replacing the
    // old dedicated secondaryLanguageCacheFile()).
    fun subtitleCacheFile(context: Context, videoPath: String, language: String): File {
        val normalizedLang = SubtitleLanguageRegistry.normalize(language) ?: language.take(2).lowercase().ifBlank { "en" }
        return File(subtitleCacheDir(context), "${subtitleCacheKey(videoPath)}.$normalizedLang.srt")
    }

    suspend fun downloadBestEnglishSubtitleDetailed(
        context: Context,
        videoPath: String
    ): SubtitleDownloadResult = downloadBestSubtitleDetailed(context, videoPath, listOf("en"))

    // Tries each language in priority order, using the same cache-check +
    // progressive search-name fallback as the English-only version above —
    // this is what "preferred languages" (spec item #10) actually drives:
    // English-then-Hindi-then-Samoan tries English's search terms first,
    // and only moves to the next language if NONE of English's fallback
    // search attempts found anything.
    suspend fun downloadBestSubtitleDetailed(
        context: Context,
        videoPath: String,
        languages: List<String>
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            findCachedSubtitle(context, videoPath, languages)?.let { cached ->
                Log.d(TAG, "Subtitle cache hit for $videoPath (${cached.language})")
                return@withContext SubtitleDownloadResult.Success(cached.uri, cached.language)
            }

            val cleanName = cleanMovieName(videoPath)
            Log.d(TAG, "Clean search name: $cleanName")

            if (cleanName.isBlank()) {
                return@withContext SubtitleDownloadResult.UnexpectedError("Could not derive a search name from the file name")
            }

            val attempts = buildSearchAttempts(cleanName)
            val languagesToTry = languages.ifEmpty { listOf("en") }
            Log.d(TAG, "Search attempts (in order): $attempts across languages: $languagesToTry")

            var fileId: Int? = null
            // FIX: previously only the fileId was captured from the search
            // loop — WHICH language actually succeeded was thrown away,
            // which is exactly how a Hindi result ended up being cached
            // and labeled as English elsewhere. Now tracked alongside it.
            var succeededLanguage: String? = null
            var lastHttpError: Pair<Int, String>? = null
            outer@ for (lang in languagesToTry) {
                for (attempt in attempts) {
                    when (val r = searchFileId(attempt, lang)) {
                        is SearchAttemptResult.Found -> { fileId = r.fileId; succeededLanguage = lang; break@outer }
                        is SearchAttemptResult.HttpError -> lastHttpError = r.code to r.bodyPreview
                        SearchAttemptResult.NoResults -> {}
                    }
                }
            }

            if (fileId == null || succeededLanguage == null) {
                if (lastHttpError != null) {
                    return@withContext SubtitleDownloadResult.SearchHttpError(lastHttpError.first, lastHttpError.second)
                }
                return@withContext SubtitleDownloadResult.NoResults(attempts)
            }

            finishDownload(subtitleCacheFile(context, videoPath, succeededLanguage), fileId, succeededLanguage)
        } catch (e: Exception) {
            Log.e(TAG, "Subtitle error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    // ── Multi-result search (Subtitle Download Search) ──────────────────
    // Returns every candidate the API found for this video (up to a
    // reasonable cap), fully parsed with the metadata the result-card UI
    // needs — release name, hearing-impaired/forced flags, download count,
    // rating, translation flags — ranked so the best match sorts first.
    // Distinct from downloadBestEnglishSubtitleDetailed above, which is the
    // fire-and-forget auto-download path used on video load; this one is
    // for the person actively browsing "Download subtitles" results.
    suspend fun searchSubtitlesDetailed(
        query: String,
        season: Int? = null,
        episode: Int? = null,
        language: String = "en",
        preferForced: Boolean = false,
        preferSdh: Boolean = false
    ): SubtitleSearchListResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SubtitleSearchListResult.NoResults
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            var searchUrl = "$BASE_URL/subtitles?query=$encoded&languages=$language&order_by=download_count&order_direction=desc"
            if (season != null) searchUrl += "&season_number=$season"
            if (episode != null) searchUrl += "&episode_number=$episode"

            val request = Request.Builder()
                .url(searchUrl).get()
                .addHeader("Api-Key", API_KEY)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    val detail = extractApiMessage(body) ?: "HTTP ${response.code}"
                    return@withContext SubtitleSearchListResult.HttpError(response.code, detail)
                }

                val json = JSONObject(body)
                val dataArray = json.optJSONArray("data") ?: return@withContext SubtitleSearchListResult.NoResults
                val results = mutableListOf<SubtitleSearchResult>()

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val files = attrs.optJSONArray("files") ?: continue
                    if (files.length() == 0) continue
                    val fileObj = files.optJSONObject(0) ?: continue
                    val fileId = fileObj.optInt("file_id", -1)
                    if (fileId <= 0) continue

                    val ratingsObj = attrs.optJSONObject("ratings")
                    val rating = attrs.optDouble("ratings", Double.NaN).let {
                        if (!it.isNaN()) it else ratingsObj?.optDouble("value", 0.0) ?: 0.0
                    }
                    val fps = attrs.optDouble("fps", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }

                    results.add(
                        SubtitleSearchResult(
                            fileId = fileId,
                            language = attrs.optString("language", language),
                            release = attrs.optString("release", "").ifBlank { fileObj.optString("file_name", "Unknown release") },
                            downloadCount = attrs.optInt("download_count", 0),
                            rating = rating,
                            hearingImpaired = attrs.optBoolean("hearing_impaired", false),
                            forced = attrs.optBoolean("foreign_parts_only", false),
                            aiTranslated = attrs.optBoolean("ai_translated", false),
                            machineTranslated = attrs.optBoolean("machine_translated", false),
                            fromTrusted = attrs.optBoolean("from_trusted", false),
                            fps = fps
                        )
                    )
                }

                if (results.isEmpty()) return@withContext SubtitleSearchListResult.NoResults

                val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
                fun matchScore(r: SubtitleSearchResult): Int {
                    val releaseLower = r.release.lowercase()
                    val wordHits = queryWords.count { releaseLower.contains(it) }
                    var score = wordHits * 100
                    if (r.fromTrusted) score += 30
                    if (!r.machineTranslated && !r.aiTranslated) score += 20
                    if (preferForced && r.forced) score += 40
                    if (!preferForced && r.forced) score -= 15
                    if (preferSdh && r.hearingImpaired) score += 40
                    if (!preferSdh && r.hearingImpaired) score -= 10
                    score += (r.downloadCount / 100).coerceAtMost(50)
                    return score
                }

                val ranked = results.sortedByDescending { matchScore(it) }.take(25)
                SubtitleSearchListResult.Success(ranked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            SubtitleSearchListResult.HttpError(0, e.message ?: e.javaClass.simpleName)
        }
    }

    // Downloads a specific, user-picked result by file_id — same
    // download-link + fetch + cache pipeline the auto-download path uses,
    // just entered from a chosen search result instead of the first hit.
    // FIX: now takes the result's actual language so it's cached under the
    // correct language-specific slot instead of always ".en.srt".
    suspend fun downloadSubtitleByFileId(
        context: Context,
        videoPath: String,
        fileId: Int,
        language: String
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            finishDownload(subtitleCacheFile(context, videoPath, language), fileId, language)
        } catch (e: Exception) {
            Log.e(TAG, "Download-by-id error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    // Downloads to an ARBITRARY target file — used by Dual Subtitles when
    // it needs precise control over the exact file location. Since
    // subtitleCacheFile() is now itself language-aware (see FIX above),
    // most callers should just use downloadSubtitleByFileId with the
    // right language instead of this; this lower-level entry point still
    // exists for that one case.
    suspend fun downloadSubtitleToFile(targetFile: File, fileId: Int, language: String): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            finishDownload(targetFile, fileId, language)
        } catch (e: Exception) {
            Log.e(TAG, "Download-to-file error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    // Shared tail end of every download path — get the download link,
    // fetch the SRT body, write it to the given target file. Pulled out
    // so the manual result-picker, the automatic best-guess path, and the
    // dual-language path can't silently diverge in how a subtitle actually
    // gets saved. Now takes `language` purely to embed it in the returned
    // Success result — the actual file location is entirely the caller's
    // decision.
    private fun finishDownload(targetFile: File, fileId: Int, language: String): SubtitleDownloadResult {
        val linkResult = getDownloadLink(fileId)
        val subtitleLink = when (linkResult) {
            is DownloadLinkResult.Found -> linkResult.link
            is DownloadLinkResult.HttpError -> return SubtitleDownloadResult.DownloadHttpError(linkResult.code, linkResult.bodyPreview)
            DownloadLinkResult.QuotaExhausted -> return SubtitleDownloadResult.QuotaExhausted
            DownloadLinkResult.EmptyLink -> return SubtitleDownloadResult.DownloadHttpError(0, "API returned no download link")
        }

        val srtResult = downloadSrt(subtitleLink)
        val srtText = when (srtResult) {
            is SrtResult.Found -> srtResult.text
            is SrtResult.HttpError -> return SubtitleDownloadResult.SrtFetchError(srtResult.code)
        }

        targetFile.parentFile?.mkdirs()
        targetFile.writeText(srtText, Charsets.UTF_8)
        Log.d(TAG, "Subtitle saved: ${targetFile.absolutePath}")
        return SubtitleDownloadResult.Success(Uri.fromFile(targetFile), language)
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

    // Public wrapper — the manual search UI pre-fills its query box with
    // this same cleaned name, so what the person edits starts from the same
    // baseline the automatic search would have tried first.
    fun cleanMovieNamePublic(videoPath: String): String = cleanMovieName(videoPath)

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

    private fun searchFileId(searchName: String, language: String = "en"): SearchAttemptResult {
        if (searchName.isBlank()) return SearchAttemptResult.NoResults

        val query = URLEncoder.encode(searchName, "UTF-8")
        val searchUrl =
            "$BASE_URL/subtitles?query=$query&languages=$language&order_by=download_count&order_direction=desc"

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
                // OpenSubtitles returns HTTP 406 (not a 200-with-remaining:0)
                // when the daily download quota is actually hit — this was
                // previously falling through to the generic error path,
                // surfacing a raw "Download blocked (406): ..." message
                // instead of the friendly quota message below.
                if (response.code == 406) {
                    Log.w(TAG, "OpenSubtitles daily download quota exceeded (HTTP 406).")
                    return DownloadLinkResult.QuotaExhausted
                }
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
