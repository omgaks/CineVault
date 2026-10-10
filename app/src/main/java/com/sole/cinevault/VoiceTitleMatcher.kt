package com.sole.cinevault

/**
 * Voice stage 2a: works out which film a spoken title means. The recogniser
 * often gets a name slightly wrong ("Avenger's end game"), so this is
 * forgiving about spelling and spacing, but it never guesses between films
 * that fit about equally well: it hands those back as a pick-list.
 */
internal data class TitleCandidate(val key: String, val title: String)
internal data class TitleMatch(val candidate: TitleCandidate, val score: Int)

internal sealed interface TitleChoice {
    /** One clear winner. Safe to play. */
    data class Play(val match: TitleCandidate) : TitleChoice
    /** Several could be meant. Show these and let the person pick. */
    data class Choose(val options: List<TitleCandidate>) : TitleChoice
    object NotFound : TitleChoice
}

private const val MIN_SCORE = 60
private const val CONFIDENT_SCORE = 95
private const val CLEAR_MARGIN = 15
private const val PICK_LIST_WINDOW = 25
private const val MAX_OPTIONS = 5

private val SKIP_WORDS = setOf("the", "a", "an", "of")
private val NUMBER_WORDS = mapOf(
    "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
    "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
)

private fun words(raw: String): List<String> =
    raw.lowercase()
        .replace("'", "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it !in SKIP_WORDS }
        .map { NUMBER_WORDS[it] ?: it }

private fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[b.length]
}

/** How many letters may differ when the whole spoken title is compared with a title. */
private fun allowedEdits(length: Int): Int = when {
    length >= 12 -> 3
    length >= 8 -> 2
    length >= 5 -> 1
    else -> 0
}

/** How well one spoken word matches one title word, 0.0 to 1.0. */
private fun wordSimilarity(spoken: String, title: String): Double = when {
    spoken == title -> 1.0
    spoken.length >= 3 && title.length >= 3 && (title.startsWith(spoken) || spoken.startsWith(title)) -> 0.85
    spoken.length >= 4 && editDistance(spoken, title) <= (if (spoken.length >= 7) 2 else 1) -> 0.8
    else -> 0.0
}

internal fun scoreTitle(spoken: String, title: String): Int {
    val q = words(spoken)
    val t = words(title)
    if (q.isEmpty() || t.isEmpty()) return 0
    if (q == t) return 100

    // "end game" vs "endgame": compare with the spaces removed too.
    val qJoined = q.joinToString("")
    val tJoined = t.joinToString("")
    if (qJoined == tJoined) return 100
    val closeJoined = qJoined.length >= 5 && editDistance(qJoined, tJoined) <= allowedEdits(tJoined.length)
    val containedJoined = qJoined.length >= 5 && tJoined.contains(qJoined)

    var matchedWeight = 0.0
    val used = mutableSetOf<Int>()
    q.forEach { qw ->
        var best = 0.0
        var bestIdx = -1
        t.forEachIndexed { idx, tw ->
            val s = wordSimilarity(qw, tw)
            if (s > best) { best = s; bestIdx = idx }
        }
        matchedWeight += best
        if (bestIdx >= 0 && best > 0.0) used += bestIdx
    }
    var coverage = matchedWeight / q.size
    if (containedJoined) coverage = maxOf(coverage, 1.0)
    if (closeJoined) coverage = maxOf(coverage, 0.9)
    if (coverage < 0.75) return 0
    val titleCoverage = if (containedJoined || closeJoined) minOf(1.0, qJoined.length.toDouble() / tJoined.length)
    else used.size.toDouble() / t.size
    return (70 * coverage + 30 * titleCoverage).toInt()
}

internal fun rankTitles(spoken: String, candidates: List<TitleCandidate>): List<TitleMatch> =
    candidates
        .map { TitleMatch(it, scoreTitle(spoken, it.title)) }
        .filter { it.score >= MIN_SCORE }
        .sortedWith(compareByDescending<TitleMatch> { it.score }.thenBy { it.candidate.title.lowercase() })

internal fun chooseTitle(spoken: String, candidates: List<TitleCandidate>): TitleChoice {
    val ranked = rankTitles(spoken, candidates)
    if (ranked.isEmpty()) return TitleChoice.NotFound
    val top = ranked.first()
    val second = ranked.getOrNull(1)
    if (second == null) return TitleChoice.Play(top.candidate)
    if (top.score >= CONFIDENT_SCORE && top.score - second.score >= 10) return TitleChoice.Play(top.candidate)
    if (top.score >= 80 && top.score - second.score >= CLEAR_MARGIN) return TitleChoice.Play(top.candidate)
    val options = ranked.filter { top.score - it.score <= PICK_LIST_WINDOW }.take(MAX_OPTIONS).map { it.candidate }
    return TitleChoice.Choose(options)
}
