package com.sole.cinevault

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

    // Keep title before year, example:
    // Guardians of the Galaxy 2014 1080p BluRay -> Guardians of the Galaxy
    val beforeYear =
        Regex("(?i)^(.*?)(19\\d{2}|20\\d{2})\\b")
            .find(original)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

    var cleaned =
        beforeYear?.takeIf { it.isNotBlank() } ?: original

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
    return cleanMovieFilename(name)
        .replace(Regex("(?i)\\b(19\\d{2}|20\\d{2})\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}