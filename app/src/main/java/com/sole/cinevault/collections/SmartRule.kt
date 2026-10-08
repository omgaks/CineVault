package com.sole.cinevault.collections

/**
 * A smart collection is just a saved filter over your own library. Stored as a
 * short readable string (no JSON / reflection), e.g.
 * "genre=Horror|Thriller;decade=1980;minRating=7.0".
 */
data class SmartRule(
    val genres: Set<String> = emptySet(),
    val decade: Int? = null,
    val minRating: Double? = null,
    val director: String? = null,
) {
    val isEmpty: Boolean
        get() = genres.isEmpty() && decade == null && minRating == null && director.isNullOrBlank()

    fun encode(): String = buildList {
        if (genres.isNotEmpty()) add("genre=" + genres.joinToString("|") { clean(it) })
        decade?.let { add("decade=$it") }
        minRating?.let { add("minRating=$it") }
        director?.takeIf { it.isNotBlank() }?.let { add("director=" + clean(it)) }
    }.joinToString(";")

    /** All parts given must match; an empty rule matches nothing. */
    fun matches(itemGenres: List<String>, year: Int?, rating: Double?, itemDirector: String?): Boolean {
        if (isEmpty) return false
        if (genres.isNotEmpty() && itemGenres.none { g -> genres.any { it.equals(g, ignoreCase = true) } }) return false
        if (decade != null && (year == null || year < decade || year > decade + 9)) return false
        if (minRating != null && (rating == null || rating < minRating)) return false
        if (!director.isNullOrBlank() && itemDirector?.contains(director, ignoreCase = true) != true) return false
        return true
    }

    fun describe(): String = buildList {
        if (genres.isNotEmpty()) add(genres.sorted().joinToString(" / "))
        decade?.let { add("${it}s") }
        minRating?.let { add("${trim(it)}+ rating") }
        director?.takeIf { it.isNotBlank() }?.let { add("by $it") }
    }.joinToString("  ·  ")

    private fun trim(d: Double) = if (d == d.toInt().toDouble()) d.toInt().toString() else d.toString()

    companion object {
        private fun clean(s: String) = s.replace(Regex("[;=|]"), " ").trim()

        fun decode(raw: String?): SmartRule? {
            if (raw.isNullOrBlank()) return null
            var genres = emptySet<String>()
            var decade: Int? = null
            var min: Double? = null
            var director: String? = null
            raw.split(";").forEach { part ->
                val key = part.substringBefore('=', "").trim()
                val value = part.substringAfter('=', "").trim()
                when (key) {
                    "genre" -> genres = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    "decade" -> decade = value.toIntOrNull()
                    "minRating" -> min = value.toDoubleOrNull()
                    "director" -> director = value.ifBlank { null }
                }
            }
            return SmartRule(genres, decade, min, director).takeUnless { it.isEmpty }
        }
    }
}
