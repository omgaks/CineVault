package com.sole.cinevault

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

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
    suspend fun search(filmName: String, season: Int?, episode: Int?, language: String): SubtitleSearchListResult = withContext(Dispatchers.IO) {
        if (filmName.isBlank()) return@withContext SubtitleSearchListResult.NoResults
        if (API_KEY.isBlank()) {
            Log.w(TAG, "SUBDL_API_KEY not configured — skipping SubDL search")
            return@withContext SubtitleSearchListResult.NoResults
        }
        try {
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

                val srtText = extractSubtitleText(bytes)
                    ?: return@withContext SubtitleDownloadResult.UnexpectedError("Couldn't find a subtitle file in the SubDL download")

                val targetFile = OpenSubtitlesClient.subtitleCacheFile(context, videoPath, language, provider = "SubDL")
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(srtText, Charsets.UTF_8)
                Log.d(TAG, "SubDL subtitle saved: ${targetFile.absolutePath}")
                SubtitleDownloadResult.Success(Uri.fromFile(targetFile), language, provider = "SubDL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SubDL download error: ${e.message}", e)
            SubtitleDownloadResult.UnexpectedError(e.message ?: e.javaClass.simpleName)
        }
    }

    // Detects a ZIP by its magic bytes (PK\x03\x04) rather than trusting
    // the URL's file extension — extracts the first .srt/.vtt/.ass/.ssa
    // entry found. Falls back to treating the response as raw text
    // directly when it isn't a ZIP at all, since SubDL's unpack=1 file
    // URLs aren't documented clearly enough to assume either way.
    private fun extractSubtitleText(bytes: ByteArray): String? {
        val isZip = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        if (!isZip) return bytes.toString(Charsets.UTF_8)

        return try {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") || name.endsWith(".ssa"))) {
                        return@use zis.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zis.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "SubDL zip extraction failed: ${e.message}", e)
            null
        }
    }
}
