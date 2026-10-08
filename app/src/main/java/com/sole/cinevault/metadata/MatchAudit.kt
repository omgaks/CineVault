package com.sole.cinevault.metadata

import com.sole.cinevault.library.extractEpisodeInfo

enum class MatchHealth { OK, DOUBTFUL, UNMATCHED }

data class MatchAudit(val health: MatchHealth, val score: Float, val reason: String)

private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "in", "to")

internal fun titleTokens(raw: String): Set<String> =
    raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(" ")
        .filter { it.isNotBlank() && it !in STOP_WORDS }
        .toSet()

/** 0..1. Dice overlap of the words; 1.0 when either title contains the other. */
internal fun titleSimilarity(a: String, b: String): Float {
    val x = titleTokens(a)
    val y = titleTokens(b)
    if (x.isEmpty() || y.isEmpty()) return 1f
    val common = x.intersect(y).size
    if (common == 0) return 0f
    val dice = 2f * common / (x.size + y.size)
    val contained = x.containsAll(y) || y.containsAll(x)
    return if (contained) maxOf(dice, 0.75f) else dice
}

private fun hasLatinLetters(s: String) = s.any { it in 'a'..'z' || it in 'A'..'Z' }

/**
 * Cheap, offline sanity check of an existing match: does the matched title
 * resemble what the file is called, and is the year close? It never changes
 * anything, it only flags what is worth a look.
 *
 * [checkTitle] should be false when the metadata language is not English, since
 * a correct match then legitimately has a different title from the file name.
 */
fun auditMatch(
    fileName: String,
    type: String,
    tmdbId: Int?,
    matchedTitle: String,
    matchedSubtitle: String,
    checkTitle: Boolean = true,
): MatchAudit {
    if (type != "movie" && type != "tv") return MatchAudit(MatchHealth.OK, 1f, "")
    if (tmdbId == null) return MatchAudit(MatchHealth.UNMATCHED, 0f, "No match found yet")

    val fileTitle = if (type == "tv") {
        extractEpisodeInfo(fileName)?.showName ?: cleanMovieFilename(fileName)
    } else cleanMovieFilename(fileName)

    if (type == "movie") {
        val fileYear = extractYearHint(fileName)?.toIntOrNull()
        val matchedYear = matchedSubtitle.take(4).toIntOrNull()
        if (fileYear != null && matchedYear != null && kotlin.math.abs(fileYear - matchedYear) > 1) {
            return MatchAudit(MatchHealth.DOUBTFUL, 0.2f, "File says $fileYear, match is $matchedYear")
        }
    }

    if (checkTitle && hasLatinLetters(matchedTitle)) {
        val score = titleSimilarity(fileTitle, matchedTitle)
        if (score < 0.5f) return MatchAudit(MatchHealth.DOUBTFUL, score, "Title differs from the file name")
        return MatchAudit(MatchHealth.OK, score, "")
    }
    return MatchAudit(MatchHealth.OK, 1f, "")
}
