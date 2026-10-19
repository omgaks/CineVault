package com.sole.cinevault.library

data class EpisodeInfo(
    val showName: String,
    val season: Int,
    val episode: Int
)

private val releaseTagWords = Regex(
    "(?i)\\b(1080p|720p|480p|2160p|4k|web|webrip|webdl|web dl|bluray|brrip|bdrip|x265|x264|hevc|h264|h265|10bit|ita|eng|dual|multi|hindi|aac|dts|truehd|atmos|pir8|amzn|nf|yts|rarbg|mkv|mp4|avi)\\b"
)

/**
 * Files named like "Show Name Episode 06" carry no season. They count as a series episode
 * (season 1) only when nothing but release tags follows the number, so a film such as
 * "Star Wars Episode 1 The Phantom Menace" is not mistaken for a show.
 */
internal fun extractBareEpisodeNumber(cleanName: String): EpisodeInfo? {
    val match = Regex("(?i)^(.*?)\\b(?:episode|ep)\\s*(\\d{1,3})\\b(.*)$").find(cleanName) ?: return null
    val show = match.groupValues[1].replace(releaseTagWords, "").replace(Regex("[\\[\\]()]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    if (show.isBlank()) return null
    val rest = match.groupValues[3].replace(releaseTagWords, "").replace(Regex("[\\[\\]()\\s]"), "")
    if (rest.isNotEmpty()) return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    return EpisodeInfo(showName = show, season = 1, episode = episode)
}

/** True for names the old SxxEyy / NxMM patterns miss but that are plainly "Show Episode NN". */
fun isBareEpisodeNumberName(fileName: String): Boolean {
    val clean = fileName.substringBeforeLast(".").replace(".", " ").replace("_", " ").replace("-", " ")
    return extractBareEpisodeNumber(clean) != null
}

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

    return extractBareEpisodeNumber(cleanName)
}

