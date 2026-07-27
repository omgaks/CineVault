package com.sole.cinevault

import java.io.File

// ── Local subtitle file matching ─────────────────────────────────────────
// Recognizes the standard naming conventions listed in the spec:
//   Movie.srt / Movie.en.srt / Movie.eng.forced.srt / Movie.en.sdh.srt
//   S01E05.en.srt
// and picks the best match for a given video file, preferring an exact
// language match over a bare unlabeled .srt, and preferring the person's
// preferred-language priority order when multiple language matches exist.
data class LocalSubtitleMatch(val file: File, val languageCode: String?, val isForced: Boolean, val isSdh: Boolean)

private val LANGUAGE_TOKEN_MAP = mapOf(
    "en" to "en", "eng" to "en", "english" to "en",
    "hi" to "hi", "hin" to "hi", "hindi" to "hi",
    "sm" to "sm", "smo" to "sm", "samoan" to "sm",
    "fr" to "fr", "fre" to "fr", "fra" to "fr", "french" to "fr",
    "es" to "es", "spa" to "es", "spanish" to "es"
)

// Parses "Movie.en.forced.srt" / "S01E05.eng.sdh.srt" / "Movie.srt" style
// filenames into their language/forced/sdh components. Returns null
// language when the file has no recognizable language token (bare
// "Movie.srt" case) rather than guessing.
private fun parseSubtitleFilename(fileName: String): Triple<String?, Boolean, Boolean> {
    val withoutExt = fileName.substringBeforeLast(".")
    val tokens = withoutExt.split(".").map { it.lowercase() }
    var language: String? = null
    var forced = false
    var sdh = false
    for (token in tokens) {
        when {
            token == "forced" -> forced = true
            token == "sdh" -> sdh = true
            LANGUAGE_TOKEN_MAP.containsKey(token) && language == null -> language = LANGUAGE_TOKEN_MAP[token]
        }
    }
    return Triple(language, forced, sdh)
}

fun findBestMatchingLocalSubtitle(videoPath: String, preferredLanguages: List<String>): LocalSubtitleMatch? {
    val videoFile = File(videoPath)
    val folder = videoFile.parentFile ?: return null
    if (!folder.isDirectory) return null

    val base = videoFile.nameWithoutExtension
    // Also matches the S01E05-style base for TV episodes, in case the
    // subtitle is named after the episode code rather than the full
    // (often much longer/messier) video filename.
    val episodeCode = Regex("[Ss](\\d{1,2})[Ee](\\d{1,2})").find(base)?.value

    val candidates = folder.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("srt", "vtt", "ass", "ssa", "ttml") }
        ?.filter { file ->
            val name = file.nameWithoutExtension
            name.equals(base, ignoreCase = true) ||
                name.startsWith("$base.", ignoreCase = true) ||
                (episodeCode != null && (name.equals(episodeCode, ignoreCase = true) || name.startsWith("$episodeCode.", ignoreCase = true)))
        }
        ?.map { file ->
            val (lang, forced, sdh) = parseSubtitleFilename(file.name)
            LocalSubtitleMatch(file, lang, forced, sdh)
        }
        ?: return null

    if (candidates.isEmpty()) return null

    // Rank: preferred-language match first (in priority order) > any
    // labeled language > bare unlabeled file. Within a language, a
    // non-forced/non-SDH "clean" copy is preferred as the default pick —
    // forced/SDH variants are still reachable manually via the Track
    // Selector, this is just what auto-load picks first.
    for (lang in preferredLanguages) {
        candidates.firstOrNull { it.languageCode == lang && !it.isForced && !it.isSdh }?.let { return it }
        candidates.firstOrNull { it.languageCode == lang }?.let { return it }
    }
    candidates.firstOrNull { it.languageCode != null }?.let { return it }
    return candidates.firstOrNull()
}
