package com.sole.cinevault.subtitles

// ── Centralized subtitle language registry ───────────────────────────────
// Previously FOUR separate, near-identical "language code -> display name"
// implementations existed across the codebase (VideoPlayerScreen.kt's
// friendlyLanguageName, SubtitleTrackSelector.kt's friendlyLanguageDisplay,
// SubtitleSearchSheet.kt's friendlyLanguageDisplay2, and
// SubtitleFileMatcher.kt's much SMALLER 5-language LANGUAGE_TOKEN_MAP) —
// meaning the local-file-matcher only recognized 5 languages while the
// rest of the app understood 12+, and any new language had to be added in
// up to four places to actually work everywhere. One list now.
//
// ISO-639-1 (2-letter) is the canonical form used throughout the app —
// OpenSubtitles' API, the preferred-language priority list, and dual-
// subtitle selection all key off this. ISO-639-2 (3-letter) codes are
// accepted as input aliases (common in embedded MKV/MP4 track metadata and
// local filename conventions) and normalized down to the 2-letter form.
data class SubtitleLanguage(val code: String, val displayName: String, val aliases: List<String>)

object SubtitleLanguageRegistry {

    private val LANGUAGES = listOf(
        SubtitleLanguage("en", "English", listOf("eng", "english")),
        SubtitleLanguage("hi", "Hindi", listOf("hin", "hindi")),
        SubtitleLanguage("sm", "Samoan", listOf("smo", "samoan")),
        SubtitleLanguage("fr", "French", listOf("fre", "fra", "french")),
        SubtitleLanguage("es", "Spanish", listOf("spa", "spanish")),
        SubtitleLanguage("it", "Italian", listOf("ita", "italian")),
        SubtitleLanguage("ja", "Japanese", listOf("jpn", "japanese")),
        SubtitleLanguage("ko", "Korean", listOf("kor", "korean")),
        SubtitleLanguage("de", "German", listOf("ger", "deu", "german")),
        SubtitleLanguage("pt", "Portuguese", listOf("por", "portuguese")),
        SubtitleLanguage("zh", "Chinese", listOf("chi", "zho", "chinese")),
        SubtitleLanguage("ar", "Arabic", listOf("ara", "arabic")),
        SubtitleLanguage("ru", "Russian", listOf("rus", "russian"))
    )

    // code (2-letter) or any alias (3-letter/full-name), lowercase -> entry
    private val LOOKUP: Map<String, SubtitleLanguage> = buildMap {
        LANGUAGES.forEach { lang ->
            put(lang.code, lang)
            lang.aliases.forEach { alias -> put(alias, lang) }
        }
    }

    // For chip/selector UI — (code, displayName) pairs in the registry's
    // defined order, used by every language-picker in the app (preferred-
    // language priority list, dual-subtitle secondary picker, etc.) so
    // they can never drift apart from each other again.
    fun allLanguages(): List<Pair<String, String>> = LANGUAGES.map { it.code to it.displayName }

    // Normalizes any recognized code/alias (2-letter, 3-letter, or full
    // English name, case-insensitive) down to its canonical 2-letter form.
    // Returns null for unrecognized input rather than guessing.
    fun normalize(rawCode: String?): String? {
        if (rawCode.isNullOrBlank()) return null
        val key = rawCode.trim().lowercase()
        return LOOKUP[key]?.code
    }

    // Friendly display name for any recognized code/alias. Falls back to
    // the raw code (uppercased) for anything not in the registry, rather
    // than a generic "Unknown" — an unrecognized-but-present code is more
    // useful shown as-is than hidden.
    fun displayName(rawCode: String?): String {
        if (rawCode.isNullOrBlank()) return "Unknown"
        val trimmed = rawCode.trim().lowercase()
        if (trimmed == "und" || trimmed == "unknown") return "Unknown"
        LOOKUP[trimmed]?.let { return it.displayName }
        return rawCode.uppercase()
    }
}
