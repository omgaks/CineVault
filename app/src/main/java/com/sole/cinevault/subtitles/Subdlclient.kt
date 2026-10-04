package com.sole.cinevault.subtitles

import com.sole.cinevault.BuildConfig
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.io.File
import java.util.concurrent.TimeUnit

// ── SubDL — second subtitle provider ─────────────────────────────────────
// Verified against SubDL's own published API docs (subdl.com/api-doc)
// before writing any of this, not guessed — same discipline as the
// OpenSubtitles hash work, since a wrong endpoint/param name here fails
// silently (empty results forever, no error to signal it's wrong).
//
// Two real differences from OpenSubtitlesClient that shape this file:
//   1. Auth is a plain `api_key` query parameter, not a bearer token or
//      header — simpler, but means the key is required on every request.
//   2. Downloads come back as a ZIP file (usually), not a direct SRT —
//      this client has to actually unzip the response, defensively
//      falling back to treating it as raw text if it somehow isn't zipped
//      (the docs don't fully guarantee zip vs. raw for every path).
//
// Deliberately NOT unified into the same request/response shape as
// OpenSubtitlesClient — SubDL's download flow (a ready-made relative URL
// straight from search results) is structurally different from
// OpenSubtitles' (a numeric file_id requiring a separate POST to get a
// download link), so forcing them into one code path would either lose
// SubDL's simpler flow or complicate OpenSubtitles' working one. They
// share the SubtitleSearchResult and SubtitleDownloadResult OUTPUT types
// so callers don't need separate handling, but the internal request logic
// stays separate.
object SubDlClient {

    private val API_KEY: String get() = BuildConfig.SUBDL_API_KEY
    private const val BASE_URL = "https://api.subdl.com/api/v1"
    private const val DOWNLOAD_BASE = "https://dl.subdl.com"
    private const val USER_AGENT = "CineVault v1.0"
    private const val TAG = "SubDlClient"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Searches by film/show name — the same cleaned name OpenSubtitlesClient
    // already derives from the filename. SubDL's response only ever covers
    // the FIRST matching title if the search is ambiguous (their own docs:
    // "results" can list several candidate titles, but "subtitles" is only
    // for the first one) — same category of risk as any filename-based
    // search, not something new introduced here.
    suspend fun search(filmName: String, season: Int?, episode: Int?, language: String, releaseFilename: String? = null): SubtitleSearchListResult = withContext(Dispatchers.IO) {
        if (filmName.isBlank()) return@withContext SubtitleSearchListResult.NoResults
        if (API_KEY.isBlank()) {
            Log.w(TAG, "SUBDL_API_KEY not configured — skipping SubDL search")
            return@withContext SubtitleSearchListResult.NoResults
        }
        try {
            // Prefer SubDL v2 Drop & Match when the actual release filename is available.
            // It ranks subtitles against the exact media filename and exposes match_score,
            // which is substantially safer than title-only matching for sync.
            if (!releaseFilename.isNullOrBlank()) {
                val encodedFilename = URLEncoder.encode(releaseFilename, "UTF-8")
                val lang = URLEncoder.encode(language.lowercase(), "UTF-8")
                val v2Url = "https://api.subdl.com/api/v2/files/search?filename=$encodedFilename&languages=$lang&engine=local&subs_per_page=25"
                val v2Request = Request.Builder()
                    .url(v2Url).get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
                httpClient.newCall(v2Request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        val json = JSONObject(body)
                        val subs = json.optJSONArray("subtitles")
                        if (subs != null && subs.length() > 0) {
                            val ranked = mutableListOf<SubtitleSearchResult>()
                            for (i in 0 until subs.length()) {
                                val sub = subs.optJSONObject(i) ?: continue
                                val downloadPath = sub.optString("url", "")
                                if (downloadPath.isBlank()) continue
                                ranked += SubtitleSearchResult(
                                    fileId = -1,
                                    language = language,
                                    release = sub.optString("release_name", "").ifBlank { sub.optString("name", "Unknown release") },
                                    downloadCount = 0,
                                    rating = 0.0,
                                    hearingImpaired = sub.optBoolean("hi", false),
                                    forced = false,
                                    aiTranslated = false,
                                    machineTranslated = false,
                                    fromTrusted = false,
                                    fps = sub.optString("fps", "").toDoubleOrNull(),
                                    provider = "SubDL",
                                    subDlDownloadPath = downloadPath,
                                    matchScore = sub.optDouble("match_score").takeIf { !it.isNaN() }
                                )
                            }
                            if (ranked.isNotEmpty()) {
                                return@withContext SubtitleSearchListResult.Success(
                                    ranked.sortedByDescending { it.matchScore ?: -1.0 }.take(25)
                                )
                            }
                        }
                    } else {
                        Log.w(TAG, "SubDL v2 filename match unavailable (${response.code}); falling back to title search")
                    }
                }
            }
            val encoded = URLEncoder.encode(filmName, "UTF-8")
            var url = "$BASE_URL/subtitles?api_key=$API_KEY&film_name=$encoded&languages=${language.uppercase()}&subs_per_page=25&hi=1"
            if (season != null) url += "&season_number=$season"
            if (episode != null) url += "&episode_number=$episode"

            val request = Request.Builder()
                .url(url).get()
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Log.w(TAG, "SubDL search failed. code=${response.code}")
                    return@withContext SubtitleSearchListResult.HttpError(response.code, "HTTP ${response.code}")
                }

                val json = JSONObject(body)
                if (!json.optBoolean("status", false)) {
                    val err = json.optString("error", "Unknown SubDL error")
                    Log.w(TAG, "SubDL reported failure: $err")
                    return@withContext SubtitleSearchListResult.HttpError(0, err)
                }

                val subsArray = json.optJSONArray("subtitles") ?: return@withContext SubtitleSearchListResult.NoResults
                val results = mutableListOf<SubtitleSearchResult>()

                for (i in 0 until subsArray.length()) {
                    val sub = subsArray.optJSONObject(i) ?: continue
                    // Full-season packs need per-episode extraction logic
                    // this first integration doesn't implement — skipped
                    // rather than downloading a whole-season zip and
                    // silently applying the wrong episode's timing.
                    if (sub.optBoolean("full_season", false)) continue
                    val downloadPath = sub.optString("url", "")
                    if (downloadPath.isBlank()) continue

                    val fps = sub.optString("fps", "").toDoubleOrNull()

                    results.add(
                        SubtitleSearchResult(
                            fileId = -1,
                            language = language,
                            release = sub.optString("release_name", "").ifBlank { sub.optString("name", "Unknown release") },
                            downloadCount = 0,
                            rating = 0.0,
                            hearingImpaired = sub.optBoolean("hi", false),
                            forced = false,
                            aiTranslated = false,
                            machineTranslated = false,
                            fromTrusted = false,
                            fps = fps,
                            provider = "SubDL",
                            subDlDownloadPath = downloadPath
                        )
                    )
                }

                if (results.isEmpty()) SubtitleSearchListResult.NoResults else SubtitleSearchListResult.Success(results.take(25))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SubDL search error: ${e.message}", e)
            SubtitleSearchListResult.HttpError(0, e.message ?: e.javaClass.simpleName)
        }
    }

    // Downloads and caches a SubDL result. Handles both the documented ZIP
    // case and a defensive raw-text fallback, since the docs don't fully
    // guarantee every download path is zipped.
    // FIX: was calling OpenSubtitlesClient.subtitleCacheFile() and building
    // the Success result WITHOUT passing provider — both silently defaulted
    // to "OpenSubtitles", so every SubDL download through this path was
    // being cached under the wrong provider slug and mislabeled in the
    // Subtitle Manager. Now explicitly tagged "SubDL" in both places.
    suspend fun downloadSubtitle(context: Context, videoPath: String, downloadPath: String, language: String): SubtitleDownloadResult = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = if (downloadPath.startsWith("http", ignoreCase = true)) downloadPath else "$DOWNLOAD_BASE$downloadPath"
            val request = Request.Builder()
                .url(downloadUrl).get()
                .addHeader("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "SubDL download failed. code=${response.code}")
                    return@withContext SubtitleDownloadResult.DownloadHttpError(response.code, "SubDL download failed")
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext SubtitleDownloadResult.DownloadHttpError(0, "Empty response from SubDL")
                }

                // Route provider payloads through the same bounded archive,
                // charset, format and candidate-ranking engine used by local/
                // website imports. This avoids first-entry-wins ZIP handling and
                // prevents ASS/VTT content from being mislabeled as SRT.
                when (val imported = SubtitleImportEngine.import(
                    context = context,
                    input = bytes.inputStream(),
                    suggestedName = downloadUrl.substringAfterLast('/').substringBefore('?'),
                    releaseHint = videoPath.substringAfterLast('/').substringAfterLast('\\'),
                    preferredLanguage = language,
                )) {
                    is SubtitleImportResult.Success -> {
                        val importedFile = imported.selected.uri.path?.let(::File)
                            ?: return@withContext SubtitleDownloadResult.UnexpectedError("Imported SubDL subtitle has no file path")
                        val cacheFile = OpenSubtitlesClient.subtitleCacheFile(
                            context, videoPath, language, provider = "SubDL"
                        )
                        importedFile.copyTo(cacheFile, overwrite = true)
                        Log.d(TAG, "SubDL subtitle cached: $cacheFile")
                        SubtitleDownloadResult.Success(
                            android.net.Uri.fromFile(cacheFile), language, provider = "SubDL"
                        )
                    }
                    is SubtitleImportResult.Failure ->
                        SubtitleDownloadResult.UnexpectedError(imported.userMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SubDL download error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

}
