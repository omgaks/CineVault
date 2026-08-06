package com.sole.cinevault.subtitles
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportedSubtitle(val uri: Uri, val displayName: String, val format: SubtitleFormat, val language: String?, val hearingImpaired: Boolean)

sealed interface SubtitleImportResult {
    data class Success(val selected: ImportedSubtitle, val alternatives: List<ImportedSubtitle> = emptyList()) : SubtitleImportResult

    data class Failure(val userMessage: String) : SubtitleImportResult
}

/**
 * Treats every website/local-file payload as untrusted.
 *
 * The engine copies only validated text subtitle data into CineVault's private
 * cache. Archives are inspected entry-by-entry and ranked for English + release
 * similarity instead of blindly loading the first file.
 */
object SubtitleImportEngine {
    private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 5L * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 30L * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 20
    private const val MAX_FILENAME_LENGTH = 120

    private val supportedExtensions = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp")

    // FIX: web_subtitles/ only ever had entries ADDED (every import/re-import
    // gets a fresh UUID-named file) — the only deletion that already existed
    // was for stray .partial files left behind by an interrupted download.
    // Every successfully-completed import stayed on disk forever, meaning
    // this directory could grow unbounded over time with no natural cap.
    // Called once at app startup (see MainActivity.kt); deletes anything
    // older than maxAgeDays, silently skipping any file it can't touch
    // (e.g. mid-write) rather than failing the whole pass.
    fun cleanOldCache(context: Context, maxAgeDays: Int = 7) {
        val cutoffMs = System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L)
        val dir = File(context.cacheDir, "web_subtitles")
        val files = dir.listFiles() ?: return
        for (file in files) {
            try {
                if (file.isFile && file.lastModified() < cutoffMs) file.delete()
            } catch (_: Exception) {
                // Best-effort cleanup — one stubborn file shouldn't block
                // the rest of the pass, and this is disposable cache data
                // either way (Android can reclaim cacheDir on its own if
                // storage gets tight).
            }
        }
    }

    suspend fun import(context: Context, input: InputStream, suggestedName: String?, releaseHint: String, preferredLanguage: String = "en"): SubtitleImportResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "web_subtitles").apply {
            mkdirs()
        }
        val payload = File(workDir, "${UUID.randomUUID()}.partial")

        try {
            FileOutputStream(payload).use { output ->
                copyBounded(input, output, MAX_DOWNLOAD_BYTES)
            }

            val header = ByteArray(4)
            val headerCount = payload.inputStream().use {
                it.read(header)
            }
            if (headerCount >= 4 && isZip(header)) {
                importZip(context, payload, releaseHint, preferredLanguage)
            } else {
                importRaw(context, payload, suggestedName, releaseHint, preferredLanguage)
            }
        } catch (`_`: PayloadTooLargeException) {
            SubtitleImportResult.Failure("The downloaded file is too large to be a subtitle.")
        } catch (`_`: Exception) {
            SubtitleImportResult.Failure("CineVault couldn't safely read this subtitle download.")
        } finally {
            payload.delete()
        }
    }

    private fun importRaw(context: Context, payload: File, suggestedName: String?, releaseHint: String, preferredLanguage: String): SubtitleImportResult {
        val bytes = payload.inputStream().use {
            readBounded(it, MAX_ENTRY_BYTES)
        }
        val detected = detectTextSubtitle(bytes) ?: return SubtitleImportResult.Failure("The website returned a page or unsupported file instead of a subtitle.")
        val displayName = safeDisplayName(suggestedName, detected.extension)
        val imported = persistValidated(context = context, bytes = detected.normalizedBytes, displayName = displayName, format = detected.format, preferredLanguage = preferredLanguage)
        return SubtitleImportResult.Success(imported)
    }

    private fun importZip(context: Context, payload: File, releaseHint: String, preferredLanguage: String): SubtitleImportResult {
        val candidates = mutableListOf<RankedCandidate>()
        var entryCount = 0
        var totalExpanded = 0L

        ZipInputStream(BufferedInputStream(payload.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    return SubtitleImportResult.Failure("This archive contains too many files.")
                }
                if (!entry.isDirectory) {
                    val leafName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    val extension = leafName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension in supportedExtensions) {
                        val bytes = try {
                            readBounded(zip, MAX_ENTRY_BYTES)
                        } catch (`_`: PayloadTooLargeException) {
                            zip.closeEntry()
                            continue
                        }
                        totalExpanded += bytes.size
                        if (totalExpanded > MAX_ARCHIVE_BYTES) {
                            return SubtitleImportResult.Failure("The extracted archive is unexpectedly large.")
                        }
                        detectTextSubtitle(bytes)?.let { detected ->
                            candidates += RankedCandidate(name = safeDisplayName(leafName, detected.extension), bytes = detected.normalizedBytes, detected = detected, score = candidateScore(fileName = leafName, releaseHint = releaseHint, preferredLanguage = preferredLanguage))
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        if (candidates.isEmpty()) {
            return SubtitleImportResult.Failure("No supported SRT, VTT, ASS, SSA or TTML subtitle was found in this archive.")
        }

        val ranked = candidates.sortedByDescending {
            it.score
        }
        val imported = ranked.map {
            persistValidated(context = context, bytes = it.bytes, displayName = it.name, format = it.detected.format, preferredLanguage = preferredLanguage)
        }
        return SubtitleImportResult.Success(imported.first(), imported.drop(1))
    }

    private fun persistValidated(context: Context, bytes: ByteArray, displayName: String, format: SubtitleFormat, preferredLanguage: String): ImportedSubtitle {
        val directory = File(context.cacheDir, "web_subtitles").apply {
            mkdirs()
        }
        val extension = displayName.substringAfterLast('.', "srt")
        val finalFile = File(directory, "${UUID.randomUUID()}.$extension")
        val partial = File(directory, "${finalFile.name}.partial")
        FileOutputStream(partial).use {
            it.write(bytes)
        }
        check(partial.renameTo(finalFile)) {
            "Could not finalize subtitle cache file"
        }

        val lower = displayName.lowercase(Locale.ROOT)
        return ImportedSubtitle(uri = Uri.fromFile(finalFile), displayName = displayName, format = format, language = detectLanguage(lower) ?: preferredLanguage, hearingImpaired = containsAny(lower, "sdh", "hearing.impaired", "hearing_impaired", "hi."))
    }

    private fun detectTextSubtitle(bytes: ByteArray): DetectedSubtitle? {
        if (bytes.isEmpty()) return null
        val hasUtf16Bom = bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        if (!hasUtf16Bom && bytes.any {
            it == 0.toByte()
        }) return null
        val decoded = decodeText(bytes).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        if (decoded.isBlank() || looksLikeHtml(decoded)) return null

        val normalized = decoded.replace("\u0000", "").toByteArray(Charsets.UTF_8)
        return when {
            Regex("""(?m)^\s*(?:\d+\s*\R)?\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{3}""").containsMatchIn(decoded) -> DetectedSubtitle(SubtitleFormat.SRT, "srt", normalized)

            decoded.startsWith("WEBVTT", ignoreCase = true) -> DetectedSubtitle(SubtitleFormat.VTT, "vtt", normalized)

            decoded.contains("[Script Info]", ignoreCase = true) && decoded.contains("[Events]", ignoreCase = true) -> DetectedSubtitle(if (decoded.contains("ScriptType: v4.00+", ignoreCase = true)) SubtitleFormat.ASS else SubtitleFormat.SSA, if (decoded.contains("ScriptType: v4.00+", ignoreCase = true)) "ass" else "ssa", normalized)

            decoded.contains("<tt", ignoreCase = true) && decoded.contains("</tt>", ignoreCase = true) -> DetectedSubtitle(SubtitleFormat.TTML, "ttml", normalized)

            else -> null
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        val charset = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return runCatching {
            bytes.toString(charset)
        }.getOrElse {
            bytes.toString(Charset.forName("windows-1252"))
        }
    }

    private fun candidateScore(fileName: String, releaseHint: String, preferredLanguage: String): Int {
        val fileTokens = tokens(fileName)
        val releaseTokens = tokens(releaseHint)
        val lower = fileName.lowercase(Locale.ROOT)
        var score = fileTokens.intersect(releaseTokens).size * 8

        if (preferredLanguage.startsWith("en") && containsAny(lower, "english", ".eng.", "_eng_", "-eng-", ".en.", "_en_")) score += 60
        if (containsAny(lower, "sdh", "hearing.impaired", "hearing_impaired")) score -= 8
        if (containsAny(lower, "forced", "foreign.only", "foreign_only")) score -= 15

        Regex("""s\d{1,2}e\d{1,3}""", RegexOption.IGNORE_CASE).find(releaseHint)?.value?.let {
            if (lower.contains(it.lowercase(Locale.ROOT))) score += 80 else score -= 30
        }
        return score
    }

    private fun safeDisplayName(suggested: String?, correctExtension: String): String {
        val rawLeaf = suggested.orEmpty().substringAfterLast('/').substringAfterLast('\\').substringBefore('?').ifBlank {
            "CineVault subtitle"
        }
        val stem = rawLeaf.substringBeforeLast('.', rawLeaf).replace(Regex("""[^A-Za-z0-9._() \-\[\]]"""), "_").trim(' ', '.', '_').ifBlank {
            "CineVault subtitle"
        }.take(MAX_FILENAME_LENGTH)
        return "$stem.$correctExtension"
    }

    private fun detectLanguage(lowerName: String): String? = when {
        containsAny(lowerName, "english", ".eng.", "_eng_", "-eng-", ".en.", "_en_") -> "en"
        containsAny(lowerName, "hindi", ".hin.", "_hin_", ".hi.", "_hi_") -> "hi"
        containsAny(lowerName, "spanish", ".spa.", "_spa_", ".es.", "_es_") -> "es"
        containsAny(lowerName, "french", ".fra.", "_fra_", ".fr.", "_fr_") -> "fr"
        else -> null
    }

    private fun tokens(value: String): Set<String> = value.lowercase(Locale.ROOT).split(Regex("""[^a-z0-9]+""")).filter {
        it.length > 1
    }.toSet()

    private fun containsAny(value: String, vararg  needles: String): Boolean = needles.any(value::contains)

    private fun looksLikeHtml(value: String): Boolean {
        val prefix = value.take(1024).lowercase(Locale.ROOT)
        return prefix.contains("<!doctype html") || prefix.contains("<html") || prefix.contains("<head") || prefix.contains("<body")
    }

    private fun isZip(header: ByteArray): Boolean = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte()) && (header[3] == 0x04.toByte() || header[3] == 0x06.toByte() || header[3] == 0x08.toByte())

    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(input, output, limit)
        return output.toByteArray()
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw PayloadTooLargeException()
            output.write(buffer, 0, count)
        }
        output.flush()
        return total
    }

    private data class DetectedSubtitle(val format: SubtitleFormat, val extension: String, val normalizedBytes: ByteArray)

    private data class RankedCandidate(val name: String, val bytes: ByteArray, val detected: DetectedSubtitle, val score: Int)

    private class PayloadTooLargeException : IllegalStateException()
}
