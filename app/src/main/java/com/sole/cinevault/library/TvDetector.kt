package com.sole.cinevault.library

data class EpisodeInfo(
    val showName: String,
    val season: Int,
    val episode: Int
)

/** Fan-sub / language tags that may trail an episode number, e.g. "(Sub)", "(Dubbed)", "v2". */
private val trailingEpisodeTags = Regex(
    "(?i)\\b(sub|subs|subbed|dub|dubbed|raw|eng sub|english sub|uncut|v\\d)\\b"
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
    val match = Regex("(?i)^(.*?)\\b(?:episode|ep|e)(?=\\s*\\d)\\s*(\\d{1,4})\\b(.*)$").find(cleanName) ?: return null
    val show = match.groupValues[1].replace(releaseTagWords, "").replace(Regex("[\\[\\]()]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    if (show.isBlank()) return null
    // "Chainsaw Man The Movie Reze Arc E01" is a film whatever number trails it.
    if (filmWords.containsMatchIn(show)) return null
    val rest = match.groupValues[3].replace(releaseTagWords, "").replace(trailingEpisodeTags, "").replace(Regex("[\\[\\]()\\s]"), "")
    if (rest.isNotEmpty()) return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    return EpisodeInfo(showName = show, season = 1, episode = episode)
}

private val filmWords = Regex("(?i)\\b(movie|film)\\b")

/**
 * "S01E05", "S01 E05", "S01E100" and double episodes such as "S01E01E02" (the first one counts).
 * Episode numbers run to four digits because long series pass 100, and some pass 1000.
 */
private val seasonEpisodeMarker = Regex("(?i)(.*?)\\bS(\\d{1,2})\\s{0,3}E(\\d{1,4})(?:\\s?E\\d{1,4})*\\b")

/** "2x05". */
private val crossMarker = Regex("(?i)(.*?)\\b(\\d{1,2})x(\\d{1,3})\\b")

/** "Season 1 Episode 3", "Season 2 Ep 14". */
private val seasonWordMarker = Regex("(?i)(.*?)\\bseason\\s*(\\d{1,2})\\s*(?:episode|ep|e)\\s*(\\d{1,4})\\b")

/** Chinese "第12集" / "第12话" (episode 12), with an optional "第2季" (season 2) before it. */
private val chineseEpisodeMarker = Regex("^(.*?)(?:第\\s*(\\d{1,2})\\s*季)?\\s*第\\s*(\\d{1,4})\\s*[集话話回]")

private fun cleanedForMarkers(fileName: String): String =
    fileName.substringBeforeLast(".").replace(".", " ").replace("_", " ").replace("-", " ")

/** True for an explicit season-and-episode marker, which is always an episode (never a film). */
fun isExplicitSeasonEpisodeName(fileName: String): Boolean {
    val clean = cleanedForMarkers(fileName)
    return seasonEpisodeMarker.containsMatchIn(clean) ||
        crossMarker.containsMatchIn(clean) ||
        seasonWordMarker.containsMatchIn(clean) ||
        extractChineseEpisodeNumber(clean) != null
}

/** "斗破苍穹 第01集": the show name is whatever comes before the marker. */
internal fun extractChineseEpisodeNumber(cleanName: String): EpisodeInfo? {
    val match = chineseEpisodeMarker.find(cleanName) ?: return null
    val show = match.groupValues[1].replace(releaseTagWords, "").replace(Regex("[\\[\\]()【】]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    if (show.isBlank()) return null
    val episode = match.groupValues[3].toIntOrNull() ?: return null
    return EpisodeInfo(showName = show, season = match.groupValues[2].toIntOrNull() ?: 1, episode = episode)
}

/** True when the name carries a bare "E01" / "Episode 1" style marker, explicit or not. */
fun hasBareEpisodeMarker(fileName: String): Boolean {
    val clean = fileName.substringBeforeLast(".").replace(".", " ").replace("_", " ").replace("-", " ")
    return Regex("(?i)\\b(?:episode|ep|e)\\s*\\d{1,4}\\b").containsMatchIn(clean) && !isExplicitSeasonEpisodeName(fileName)
}

/** True for names the old SxxEyy / NxMM patterns miss but that are plainly "Show Episode NN". */
fun isBareEpisodeNumberName(fileName: String): Boolean {
    val clean = fileName.substringBeforeLast(".").replace(".", " ").replace("_", " ").replace("-", " ")
    return extractBareEpisodeNumber(clean) != null || extractDashEpisodeNumber(fileName) != null
}

fun extractEpisodeInfo(fileName: String): EpisodeInfo? {

    val cleanName =
        fileName
            .substringBeforeLast(".")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")

    val patterns = listOf(seasonEpisodeMarker, seasonWordMarker, crossMarker)

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

    return extractChineseEpisodeNumber(cleanName)
        ?: extractBareEpisodeNumber(cleanName)
        ?: extractDashEpisodeNumber(fileName)
}

/**
 * Fan-sub style "[Group] Show Name - 05 [1080p].mkv". The number must follow a spaced dash and
 * the name must carry fan-sub evidence (a leading [group] or a trailing tag), so a film such as
 * "Catch-22" or "Mission - 13" with no such evidence is never mistaken for an episode.
 */
internal fun extractDashEpisodeNumber(fileName: String): EpisodeInfo? {
    val base = fileName.substringBeforeLast(".").replace("_", " ")
    val match = Regex("^(.*?)\\s-\\s(\\d{1,4})(?:v\\d)?\\b(.*)$").find(base) ?: return null
    // "[Group] Some Film - 2019 (1080p)" carries a release year, not episode 2019.
    if (match.groupValues[2].toIntOrNull() in 1900..2099) return null
    val hasGroup = Regex("^\\s*\\[[^\\]]+\\]").containsMatchIn(match.groupValues[1])
    val tail = match.groupValues[3]
    val hasTail = releaseTagWords.containsMatchIn(tail) || trailingEpisodeTags.containsMatchIn(tail) ||
        Regex("[\\[(]").containsMatchIn(tail)
    if (!hasGroup && !hasTail) return null
    val show = match.groupValues[1].replace(Regex("^\\s*\\[[^\\]]+\\]"), "").replace(".", " ")
        .replace(releaseTagWords, "").replace(Regex("[\\[\\]()]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    if (show.isBlank()) return null
    val rest = tail.replace(releaseTagWords, "").replace(trailingEpisodeTags, "")
        .replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("[()\\s]"), "")
    if (rest.isNotEmpty()) return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    return EpisodeInfo(showName = show, season = 1, episode = episode)
}

