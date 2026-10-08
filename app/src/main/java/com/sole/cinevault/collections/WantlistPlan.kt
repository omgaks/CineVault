package com.sole.cinevault.collections

enum class WantStatus { AVAILABLE, UPCOMING, OWNED }

data class WantItem(
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val releaseDate: String?,
    val collectionId: Int?,
    val addedAtMs: Long,
)

data class WantRow(val film: WantItem, val status: WantStatus, val label: String)

/** Pure ordering and wording for the wantlist screen. */
object WantlistPlanner {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun plan(items: List<WantItem>, ownedMovieIds: Set<Int>, todayIso: String): List<WantRow> {
        val rows = items.map { film ->
            val date = film.releaseDate?.takeIf { it.isNotBlank() }
            when {
                film.tmdbId in ownedMovieIds -> WantRow(film, WantStatus.OWNED, "In your library")
                date == null -> WantRow(film, WantStatus.UPCOMING, "Date to be announced")
                date > todayIso -> WantRow(film, WantStatus.UPCOMING, "Coming ${prettyDate(date)}")
                else -> WantRow(film, WantStatus.AVAILABLE, "Out now" + (yearOf(date)?.let { " · $it" } ?: ""))
            }
        }
        return rows.sortedWith(
            compareBy<WantRow> { it.status.ordinal }
                .thenBy {
                    when (it.status) {
                        WantStatus.AVAILABLE -> -it.film.addedAtMs
                        WantStatus.UPCOMING -> 0L
                        WantStatus.OWNED -> 0L
                    }
                }
                .thenBy { if (it.status == WantStatus.UPCOMING) it.film.releaseDate.orEmpty().ifBlank { "9999" } else "" }
                .thenBy { it.film.title.lowercase() }
        )
    }

    fun prettyDate(iso: String): String {
        val p = iso.split("-")
        val y = p.getOrNull(0)?.toIntOrNull()
        val m = p.getOrNull(1)?.toIntOrNull()
        val d = p.getOrNull(2)?.toIntOrNull()
        if (y == null || m == null || m !in 1..12) return iso
        return if (d != null) "$d ${MONTHS[m - 1]} $y" else "${MONTHS[m - 1]} $y"
    }

    private fun yearOf(iso: String): String? = iso.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }

    fun summary(rows: List<WantRow>): String {
        if (rows.isEmpty()) return "Nothing on your wantlist yet."
        val find = rows.count { it.status == WantStatus.AVAILABLE }
        val soon = rows.count { it.status == WantStatus.UPCOMING }
        val own = rows.count { it.status == WantStatus.OWNED }
        return buildList {
            if (find > 0) add("$find to find")
            if (soon > 0) add("$soon coming")
            if (own > 0) add("$own now in your library")
        }.joinToString("  ·  ")
    }
}
