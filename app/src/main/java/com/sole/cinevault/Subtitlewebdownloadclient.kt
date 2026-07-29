package com.sole.cinevault
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object SubtitleWebPolicy {
    private val navigationHosts = setOf("opensubtitles.com", "www.opensubtitles.com", "dl.opensubtitles.com")

    fun isAllowed(uri: Uri): Boolean = uri.scheme.equals("https", ignoreCase = true) && uri.host?.lowercase(Locale.ROOT) in navigationHosts

    fun searchUri(query: String): Uri {
        val cleaned = query.trim().replace(Regex("""\s+"""), " ")
        return Uri.parse("https://www.opensubtitles.com/en/search-all/q-${Uri.encode(cleaned)}")
    }
}

sealed interface WebSubtitleDownloadResult {
    data class Imported(val result: SubtitleImportResult) : WebSubtitleDownloadResult
    data class Failed(val message: String) : WebSubtitleDownloadResult
}

object SubtitleWebDownloadClient {
    private const val MAX_REDIRECTS = 5

    suspend fun downloadAndImport(context: Context, initialUrl: String, userAgent: String?, contentDisposition: String?, pageUrl: String?, releaseHint: String, preferredLanguage: String): WebSubtitleDownloadResult = withContext(Dispatchers.IO) {
        var current = runCatching {
            Uri.parse(initialUrl)
        }.getOrNull() ?: return@withContext WebSubtitleDownloadResult.Failed("The website supplied an invalid download link.")
        var redirects = 0

        while (true) {
            if (!SubtitleWebPolicy.isAllowed(current)) {
                return@withContext WebSubtitleDownloadResult.Failed("For your safety, CineVault blocked a download from an unapproved website.")
            }

            val connection = (URL(current.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/zip, application/x-subrip, text/plain, */*;q=0.5")
                userAgent?.takeIf {
                    it.isNotBlank()
                }?.let {
                    setRequestProperty("User-Agent", it)
                }
                pageUrl?.let { referrer ->
                    val referrerUri = Uri.parse(referrer)
                    if (SubtitleWebPolicy.isAllowed(referrerUri)) setRequestProperty("Referer", referrer)
                }
                CookieManager.getInstance().getCookie(current.toString())?.takeIf {
                    it.isNotBlank()
                }?.let {
                    setRequestProperty("Cookie", it)
                }
            }

            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    if (++redirects > MAX_REDIRECTS) {
                        return@withContext WebSubtitleDownloadResult.Failed("The download redirected too many times.")
                    }
                    val location = connection.getHeaderField("Location") ?: return@withContext WebSubtitleDownloadResult.Failed("The website returned an incomplete redirect.")
                    current = Uri.parse(URL(URL(current.toString()), location).toString())
                    continue
                }

                if (status !in 200..299) {
                    return@withContext WebSubtitleDownloadResult.Failed(when(status) {
                        401, 403 -> "OpenSubtitles requires you to sign in or approve this download."
                        404 -> "This subtitle download is no longer available."
                        429 -> "The website is temporarily limiting downloads. Please try again later."
                        else -> "The website rejected this download (HTTP $status)."
                    })
                }

                val name = parseFileName(connection.getHeaderField("Content-Disposition") ?: contentDisposition)
                val imported = connection.inputStream.use {
                    SubtitleImportEngine.import(context = context, input = it, suggestedName = name, releaseHint = releaseHint, preferredLanguage = preferredLanguage)
                }
                return@withContext WebSubtitleDownloadResult.Imported(imported)
            } catch (`_`: java.net.SocketTimeoutException) {
                return@withContext WebSubtitleDownloadResult.Failed("The subtitle download timed out.")
            } catch (`_`: Exception) {
                return@withContext WebSubtitleDownloadResult.Failed("CineVault couldn't download this subtitle.")
            } finally {
                connection.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable: every branch above returns from the loop")
    }

    private fun parseFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.getOrNull(1)?.let {
            return runCatching {
                URLDecoder.decode(it.trim(), StandardCharsets.UTF_8.name())
            }.getOrNull()
        }
        return Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.getOrNull(1) ?: Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.getOrNull(1)?.trim()
    }
}
