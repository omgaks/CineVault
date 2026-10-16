package com.sole.cinevault.library

data class EpisodeInfo(
    val showName: String,
    val season: Int,
    val episode: Int
)

fun extractEpisodeInfo(fileName: String): EpisodeInfo? {

    val cleanName =
        fileName
            .substringBeforeLast(".")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")

    val patterns =
        listOf(
            Regex("(?i)(.*?)\\bS(\\d{1,2})E(\\d{1,2})\\b"),
            Regex("(?i)(.*?)\\b(\\d{1,2})x(\\d{1,2})\\b")
        )

    for (pattern in patterns) {

        val match =
            pattern.find(cleanName) ?: continue

        val rawShowName =
            match.groupValues[1].trim()

        val showName =
            rawShowName
                .replace(
                    Regex(
                        "(?i)\\b(1080p|720p|2160p|4k|web|webrip|webdl|web dl|bluray|brrip|x265|x264|hevc|h264|h265|ita|eng|hindi|aac|dts|truehd|atmos|pir8|amzn|nf|yts|rarbg)\\b"
                    ),
                    ""
                )
                .replace(Regex("\\s+"), " ")
                .trim()

        return EpisodeInfo(
            showName = showName.ifBlank { rawShowName },
            season = match.groupValues[2].toIntOrNull() ?: 1,
            episode = match.groupValues[3].toIntOrNull() ?: 1
        )
    }

    // Support titles such as "Cyberpunk Edgerunners Episode 09.mkv".
    // The parent folder's season is resolved by the caller when available.
    val namedEpisode = Regex("(?i)^(.*?)\\\\b(?:episode|ep)[ ._-]*(\\\\d{1,3})\\\\b").find(cleanName)
    if (namedEpisode != null) {
        val showName = namedEpisode.groupValues[1].trim()
        val episode = namedEpisode.groupValues[2].toIntOrNull()
        if (showName.isNotBlank() && episode != null && episode > 0) {
            return EpisodeInfo(showName, 1, episode)
        }
    }

    return null
}

