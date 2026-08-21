package com.sole.cinevault.metadata

fun cleanMovieFilename(name: String): String {
    val original =
        name.substringBeforeLast(".")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // Strip edition/anniversary/cut tags BEFORE year detection, since these
    // often sit where a year would and can confuse titles that have no year
    // at all, e.g. "Akira 30th Anniversary" -> "Akira"
    val editionStripped =
        original
            .replace(Regex("(?i)\\b\\d{1,3}(st|nd|rd|th)\\s+anniversary\\b"), "")
            .replace(Regex("(?i)\\banniversary\\s+edition\\b"), "")
            .replace(Regex("(?i)\\banniversary\\b"), "")
            .replace(
                Regex(
                    "(?i)\\b(extended|theatrical|director'?s|unrated|uncut|remastered|restored|special|ultimate|collector'?s|definitive|final)\\s+(cut|edition|version)\\b"
                ),
                ""
            )
            .replace(Regex("(?i)\\b(remastered|restored|uncensored|colorized)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    // Treat the LAST year-shaped token as the release year. A title may
    // legitimately begin with a number that looks like a year:
    // "2001 A Space Odyssey 1968" and "Blade Runner 2049 2017".
    // A single leading token is therefore considered part of the title.
    val yearMatches = Regex("\\b(19|20)\\d{2}\\b").findAll(editionStripped).toList()
    val releaseYearMatch = yearMatches.lastOrNull()?.takeUnless {
        yearMatches.size == 1 && it.range.first == 0
    }
    val beforeYear = releaseYearMatch
        ?.let { editionStripped.substring(0, it.range.first) }
        ?.trim()
    var cleaned =
        beforeYear?.takeIf { it.isNotBlank() } ?: editionStripped
    cleaned =
        cleaned.replace(
            Regex(
                "(?i)\\b(3d|hsbs|sbs|half sbs|ou|half ou|480p|720p|1080p|2160p|4320p|4k|8k|10bit|8bit|x264|x265|h264|h265|h 264|h 265|hevc|av1|aac|ac3|eac3|ddp|ddp5|dd5|dd|dts|truehd|atmos|5 1|7 1|2 0|2ch|5ch|6ch|7ch|8ch|bluray|blu ray|brrip|hdrip|webdl|web dl|web|dl|webrip|dvdrip|hulu|amzn|amazon|nf|netflix|ma|psa|yts|rarbg|tigole|galaxyrg|galaxy|rg|rg265|pahe|playweb|neonoir|dubbed|dual|audio|proper|repack|remux|hdr|hdr10|dv|dolby|vision|subs|esub|multi|imax|ita|eng|hindi|yify)\\b"
            ),
            ""
        )
            .replace(Regex("(?i)\\bS\\d{1,2}E\\d{1,2}\\b"), "")
            .replace(Regex("(?i)\\b\\d{1,2}x\\d{1,2}\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    return cleaned
        .ifBlank { original }
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
}
fun tmdbMovieSearchQuery(name: String): String {
    // cleanMovieFilename already removes the detected RELEASE year while
    // preserving year-shaped numbers that are genuinely part of a title.
    return cleanMovieFilename(name)
}

// Pulls a plausible release year straight out of the ORIGINAL filename
// (before cleanMovieFilename strips it), e.g. "Movie.Name.2001.1080p.mkv"
// -> "2001". Used to disambiguate TMDB search results generically — any
// title with a same-named remake/different-year release benefits from
// this, not just one specific film. Returns null rather than guessing
// when no year-shaped token is present, same convention as the rest of
// this file's cleaning functions.
fun extractYearHint(name: String): String? {
    val normalized = name.substringBeforeLast(".").replace('.', ' ').trim()
    val matches = Regex("\\b(19|20)\\d{2}\\b").findAll(normalized).toList()
    return matches.lastOrNull()
        ?.takeUnless { matches.size == 1 && it.range.first == 0 }
        ?.value
}
