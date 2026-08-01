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
    // Provider now carried through too — needed so a cache hit and a fresh
    // download both know (and can display) which provider a subtitle
    // actually came from, not just its language.
    data class Success(val uri: Uri, val language: String, val provider: String = "OpenSubtitles") : SubtitleDownloadResult()
    data class SearchHttpError(val code: Int, val detail: String) : SubtitleDownloadResult()
    data class NoResults(val triedTerms: List<String>) : SubtitleDownloadResult()
    data class DownloadHttpError(val code: Int, val detail: String) : SubtitleDownloadResult()
    object QuotaExhausted : SubtitleDownloadResult()
    data class SrtFetchError(val code: Int) : SubtitleDownloadResult()
    data class UnexpectedError(val detail: String) : SubtitleDownloadResult()

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
    val fps: Double?,
    val hashMatch: Boolean = false,
    val provider: String = "OpenSubtitles",
    val subDlDownloadPath: String? = null
) {
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

data class CachedSubtitle(val uri: Uri, val language: String, val provider: String = "OpenSubtitles")

object OpenSubtitlesClient {

    private val API_KEY: String get() = BuildConfig.OPENSUB_API_KEY
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "CineVault v1.0"
    private const val TAG = "OpenSubtitlesClient"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadBestEnglishSubtitle(context: Context, videoPath: String): Uri? {
        val result = downloadBestEnglishSubtitleDetailed(context, videoPath)
        return (result as? SubtitleDownloadResult.Success)?.uri
    }

    // FIX: filenames now carry the provider (see subtitleCacheFile below),
    // so a straight path construction can't be used for lookups anymore —
    // this scans for whichever provider-tagged file exists for this
    // language, PLUS the old no-provider filename for anything cached
    // before this change shipped (real users have real files on disk
    // under the old scheme; those must keep resolving as cache hits, not
    // suddenly look like cache misses after an update).
    fun findCachedSubtitle(context: Context, videoPath: String, preferredLanguages: List<String>): CachedSubtitle? {
        for (lang in preferredLanguages.ifEmpty { listOf("en") }) {
            findCachedSubtitleForLanguage(context, videoPath, lang)?.let { return it }
        }
        return null
    }

    fun findCachedSubtitle(context: Context, videoPath: String, language: String): CachedSubtitle? =
        findCachedSubtitleForLanguage(context, videoPath, language)

    private fun findCachedSubtitleForLanguage(context: Context, videoPath: String, language: String): CachedSubtitle? {
        val normalizedLang = SubtitleLanguageRegistry.normalize(language) ?: language.take(2).lowercase().ifBlank { "en" }
        val key = subtitleCacheKey(videoPath)
        val prefix = "$key.$normalizedLang."
        val legacyFile = File(subtitleCacheDir(context), "$key.$normalizedLang.srt")

        // New scheme: <hash>.<lang>.<providerSlug>.srt
        val match = subtitleCacheDir(context).listFiles { f ->
            f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".srt") && f.length() > 0
        }?.firstOrNull()
        if (match != null) {
            val slug = match.name.removePrefix(prefix).removeSuffix(".srt")
            return CachedSubtitle(Uri.fromFile(match), language, providerFromSlug(slug))
        }

        // Legacy scheme: <hash>.<lang>.srt (no provider segment) — always
        // meant OpenSubtitles, since SubDL support didn't exist yet when
        // files were written this way.
        if (legacyFile.exists() && legacyFile.length() > 0) {
            return CachedSubtitle(Uri.fromFile(legacyFile), language, "OpenSubtitles")
        }
        return null
    }

    // ── Subtitle Manager support ─────────────────────────────────────────
    // Lists every language currently cached for THIS specific video — used
    // by the Subtitle Manager UI (Studio) to show what's already downloaded
    // and let the person delete individual ones. There's no separate index
    // tracking "which languages exist for which video"; each language gets
    // its own file (<hash>.<lang>.srt, see subtitleCacheFile below) inside
    // one shared cache directory, so the filenames on disk ARE the source
    // of truth — this just scans for every file sharing this video's own
    // hash prefix and pulls the language back out of each name.
    // NOTE: this only knows the language, not which provider (OpenSubtitles
    // vs SubDL) supplied it — that isn't persisted anywhere per-file today.
    // A Subtitle Manager UI built on this can show language + delete, not
    // provider, unless a small sidecar/metadata file is added later.
    fun listCachedSubtitlesForVideo(context: Context, videoPath: String): List<CachedSubtitle> {
        val key = subtitleCacheKey(videoPath)
        val prefix = "$key."
        val files = subtitleCacheDir(context).listFiles { f ->
            f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".srt") && f.length() > 0
        } ?: return emptyList()
        return files.mapNotNull { file ->
            // Two possible shapes now: "<lang>.<providerSlug>.srt" (new) or
            // just "<lang>.srt" (legacy, pre-provider-tracking — always
            // OpenSubtitles).
            val remainder = file.name.removePrefix(prefix).removeSuffix(".srt")
            val parts = remainder.split(".")
            val lang = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val provider = parts.getOrNull(1)?.let { providerFromSlug(it) } ?: "OpenSubtitles"
            CachedSubtitle(Uri.fromFile(file), lang, provider)
        }.sortedBy { SubtitleLanguageRegistry.displayName(it.language) }
    }

    // Deletes directly off the CachedSubtitle's own file — the Manager UI
    // already has the exact CachedSubtitle it's showing, so there's no
    // need to reconstruct a filename (and risk getting the provider slug
    // wrong) just to find the file again.
    fun deleteCachedSubtitle(context: Context, cached: CachedSubtitle): Boolean {
        val path = cached.uri.path ?: return false
        val file = File(path)
        return file.exists() && file.delete()
    }

    // Kept for any older call site still passing language alone — deletes
    // whichever provider's file is currently cached for that language.
    fun deleteCachedSubtitle(context: Context, videoPath: String, language: String): Boolean {
        val cached = findCachedSubtitleForLanguage(context, videoPath, language) ?: return false
        return deleteCachedSubtitle(context, cached)
    }

    // "OpenSubtitles" -> "opensubtitles", "SubDL" -> "subdl" — the only two
    // providers CineVault has today. Anything unrecognized (a slug from a
    // future provider this code doesn't know about yet) falls back to
    // showing the raw slug rather than silently mislabeling it.
    private fun providerSlug(provider: String): String = provider.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun providerFromSlug(slug: String): String = when (slug) {
        "opensubtitles" -> "OpenSubtitles"
        "subdl" -> "SubDL"
        else -> slug.ifBlank { "OpenSubtitles" }
    }

    private fun subtitleCacheDir(context: Context): File {
        val dir = File(context.filesDir, "subtitles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun normalizedPathForCacheKey(videoPath: String): String {
        val decoded = try {
            java.net.URLDecoder.decode(videoPath, "UTF-8")
        } catch (_: Exception) {
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
            normalized.replace(Regex("[^A-Za-z0-9]"), "_").takeLast(80)
        }
    }

    // FIX: filename now includes the provider (<hash>.<lang>.<providerSlug>.srt,
    // was <hash>.<lang>.srt) so a language downloaded from both OpenSubtitles
    // and SubDL gets two distinct cache entries instead of the second
    // silently overwriting the first — and so the Subtitle Manager can show
    // which provider each cached file actually came from. Provider defaults
    // to OpenSubtitles so every pre-existing call site that doesn't know
    // about providers still compiles and behaves the same as before.
    fun subtitleCacheFile(context: Context, videoPath: String, language: String, provider: String = "OpenSubtitles"): File {
        val normalizedLang = SubtitleLanguageRegistry.normalize(language) ?: language.take(2).lowercase().ifBlank { "en" }
        return File(subtitleCacheDir(context), "${subtitleCacheKey(videoPath)}.$normalizedLang.${providerSlug(provider)}.srt")
    }

    suspend fun downloadBestEnglishSubtitleDetailed(
        context: Context,
        videoPath: String
    ): SubtitleDownloadResult = downloadBestSubtitleDetailed(context, videoPath, listOf("en"))

    suspend fun downloadBestSubtitleDetailed(
        context: Context,
        videoPath: String,
        languages: List<String>
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            findCachedSubtitle(context, videoPath, languages)?.let { cached ->
                Log.d(TAG, "Subtitle cache hit for $videoPath (${cached.language})")
                return@withContext SubtitleDownloadResult.Success(cached.uri, cached.language, cached.provider)
            }

            val languagesToTry = languages.ifEmpty { listOf("en") }

            val movieHash = MovieHash.compute(videoPath)
            if (movieHash != null) {
                for (lang in languagesToTry) {
                    val hashResult = searchByHash(movieHash, lang)
                    val bestHashMatch = (hashResult as? SubtitleSearchListResult.Success)?.results?.firstOrNull { it.hashMatch }
                    if (bestHashMatch != null) {
                        Log.d(TAG, "Hash match found for $videoPath ($lang)")
                        return@withContext finishDownload(subtitleCacheFile(context, videoPath, lang), bestHashMatch.fileId, lang)
                    }
                }
            }

            val cleanName = cleanMovieName(videoPath)
            Log.d(TAG, "Clean search name: $cleanName")

            if (cleanName.isBlank()) {
                return@withContext SubtitleDownloadResult.UnexpectedError("Could not derive a search name from the file name")
            }

            val attempts = buildSearchAttempts(cleanName)
            Log.d(TAG, "Search attempts (in order): $attempts across languages: $languagesToTry")

            var fileId: Int? = null
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
                for (lang in languagesToTry) {
                    val subDlResult = SubDlClient.search(cleanName, null, null, lang)
                    val best = (subDlResult as? SubtitleSearchListResult.Success)?.results?.firstOrNull()
                    if (best?.subDlDownloadPath != null) {
                        Log.d(TAG, "SubDL fallback match found for $videoPath ($lang)")
                        return@withContext SubDlClient.downloadSubtitle(context, videoPath, best.subDlDownloadPath, lang)
                    }
                }

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

    suspend fun searchByHash(hash: String, language: String = "en"): SubtitleSearchListResult = withContext(Dispatchers.IO) {
        if (hash.isBlank()) return@withContext SubtitleSearchListResult.NoResults
        try {
            val searchUrl = "$BASE_URL/subtitles?moviehash=$hash&languages=$language&order_by=download_count&order_direction=desc"
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

                val dataArray = JSONObject(body).optJSONArray("data") ?: return@withContext SubtitleSearchListResult.NoResults
                val results = parseSearchResultsJson(dataArray, language)
                if (results.isEmpty()) return@withContext SubtitleSearchListResult.NoResults

                val ranked = results.sortedWith(
                    compareByDescending<SubtitleSearchResult> { it.hashMatch }
                        .thenByDescending { it.fromTrusted }
                        .thenByDescending { !it.machineTranslated && !it.aiTranslated }
                        .thenByDescending { it.downloadCount }
                ).take(25)
                SubtitleSearchListResult.Success(ranked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hash search error: ${e.message}", e)
            SubtitleSearchListResult.HttpError(0, e.message ?: e.javaClass.simpleName)
        }
    }

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

                val dataArray = JSONObject(body).optJSONArray("data") ?: return@withContext SubtitleSearchListResult.NoResults
                val results = parseSearchResultsJson(dataArray, language)

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

    private fun parseSearchResultsJson(dataArray: org.json.JSONArray, fallbackLanguage: String): List<SubtitleSearchResult> {
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
                    language = attrs.optString("language", fallbackLanguage),
                    release = attrs.optString("release", "").ifBlank { fileObj.optString("file_name", "Unknown release") },
                    downloadCount = attrs.optInt("download_count", 0),
                    rating = rating,
                    hearingImpaired = attrs.optBoolean("hearing_impaired", false),
                    forced = attrs.optBoolean("foreign_parts_only", false),
                    aiTranslated = attrs.optBoolean("ai_translated", false),
                    machineTranslated = attrs.optBoolean("machine_translated", false),
                    fromTrusted = attrs.optBoolean("from_trusted", false),
                    fps = fps,
                    hashMatch = attrs.optBoolean("moviehash_match", false)
                )
            )
        }
        return results
    }

    suspend fun downloadSubtitleByFileId(
        context: Context,
        videoPath: String,
        fileId: Int,
        language: String,
        provider: String = "OpenSubtitles"
    ): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            finishDownload(subtitleCacheFile(context, videoPath, language, provider), fileId, language, provider)
        } catch (e: Exception) {
            Log.e(TAG, "Download-by-id error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun downloadSubtitleToFile(targetFile: File, fileId: Int, language: String, provider: String = "OpenSubtitles"): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            finishDownload(targetFile, fileId, language, provider)
        } catch (e: Exception) {
            Log.e(TAG, "Download-to-file error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun finishDownload(targetFile: File, fileId: Int, language: String, provider: String = "OpenSubtitles"): SubtitleDownloadResult {
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
        return SubtitleDownloadResult.Success(Uri.fromFile(targetFile), language, provider)
    }

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
