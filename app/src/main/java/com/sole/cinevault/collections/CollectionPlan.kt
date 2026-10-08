package com.sole.cinevault.collections

import com.sole.cinevault.VideoWithMetadata

/**
 * Pure collection logic — no Android, no Compose, no network — so every rule
 * about what is owned, missing, upcoming, or "next up" is unit-testable.
 *
 * Matching is by TMDB id only, never by title guesswork.
 */

/** One film as TMDB lists it inside a collection. */
data class CollectionPart(
    val tmdbId: Int,
    val title: String,
    val releaseDate: String?,
    val posterPath: String?,
    val overview: String?
)

data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val parts: List<CollectionPart>
)

enum class SlotStatus { OWNED, MISSING, UPCOMING }

data class CollectionSlot(
    val part: CollectionPart,
    val status: SlotStatus,
    /** 1-based position among released films, in release order. Null when upcoming. */
    val number: Int?,
    val owned: VideoWithMetadata?,
    /** For a missing film sitting between two owned ones: their numbers. */
    val gapAfter: Int?,
    val gapBefore: Int?
) {
    val isGap: Boolean get() = gapAfter != null && gapBefore != null
    val year: String? get() = part.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
}

data class CollectionPlan(
    val slots: List<CollectionSlot>,
    val ownedCount: Int,
    val releasedTotal: Int,
    val upcomingCount: Int,
    val orderedOwned: List<VideoWithMetadata>,
    val nextUp: VideoWithMetadata?,
    /** True when TMDB's list was available; false = built from owned films only. */
    val hasFullList: Boolean
) {
    val isComplete: Boolean get() = releasedTotal > 0 && ownedCount >= releasedTotal
    val progress: Float
        get() = if (releasedTotal == 0) 0f else (ownedCount.toFloat() / releasedTotal).coerceIn(0f, 1f)
}

object CollectionPlanner {

    /** A collection earns a shelf card once the user owns this many of its films. */
    const val MIN_OWNED_FOR_SHELF = 2

    fun earnsShelfCard(ownedCount: Int): Boolean = ownedCount >= MIN_OWNED_FOR_SHELF

    /** Distinct owned films per collection id (duplicates of one film count once). */
    fun ownedCountsByCollection(videos: List<VideoWithMetadata>): Map<Int, Int> =
        videos.asSequence()
            .filter { it.collectionId != null && it.tmdbId != null }
            .groupBy { it.collectionId!! }
            .mapValues { (_, list) -> list.mapNotNull { it.tmdbId }.distinct().size }

    /** Parts already released as of [todayIso] ("yyyy-MM-dd"). Blank/future dates don't count. */
    fun releasedCount(parts: List<CollectionPart>, todayIso: String): Int =
        parts.count { isReleased(it.releaseDate, todayIso) }

    fun plan(
        details: CollectionDetails?,
        ownedItems: List<VideoWithMetadata>,
        todayIso: String,
        isFinished: (VideoWithMetadata) -> Boolean
    ): CollectionPlan {
        // One owned file per TMDB id (first wins), in the order given.
        val ownedById = LinkedHashMap<Int, VideoWithMetadata>()
        ownedItems.forEach { v -> v.tmdbId?.let { ownedById.putIfAbsent(it, v) } }

        val listed = details?.parts.orEmpty()
        val listedIds = listed.map { it.tmdbId }.toSet()
        // Owned films TMDB doesn't list (stale cache, newly added sequel):
        // keep them in the sequence rather than silently dropping them.
        val extras = ownedById.filterKeys { it !in listedIds }.map { (id, v) ->
            CollectionPart(id, v.title, null, null, v.overview)
        }

        // Status first, so upcoming films always sort after everything released.
        val withStatus = (listed + extras).map { p ->
            p to when {
                ownedById.containsKey(p.tmdbId) -> SlotStatus.OWNED
                isReleased(p.releaseDate, todayIso) -> SlotStatus.MISSING
                else -> SlotStatus.UPCOMING
            }
        }.sortedWith(
            compareBy<Pair<CollectionPart, SlotStatus>>(
                { it.second == SlotStatus.UPCOMING },
                { it.first.releaseDate.isNullOrBlank() },
                { it.first.releaseDate.orEmpty() },
                { it.first.title.lowercase() }
            )
        )
        val sorted = withStatus.map { it.first }
        val statuses = withStatus.map { it.second }

        // Numbering: released films only, in release order.
        var counter = 0
        val numbers = statuses.map { if (it == SlotStatus.UPCOMING) null else ++counter }

        // Second pass: gaps (a missing film with an owned film somewhere before AND after it).
        val slots = sorted.mapIndexed { i, p ->
            var gapAfter: Int? = null
            var gapBefore: Int? = null
            if (statuses[i] == SlotStatus.MISSING) {
                val prev = (i - 1 downTo 0).firstOrNull { statuses[it] == SlotStatus.OWNED }
                val next = (i + 1 until sorted.size).firstOrNull { statuses[it] == SlotStatus.OWNED }
                if (prev != null && next != null) {
                    gapAfter = numbers[prev]
                    gapBefore = numbers[next]
                }
            }
            CollectionSlot(p, statuses[i], numbers[i], ownedById[p.tmdbId], gapAfter, gapBefore)
        }

        val orderedOwned = slots.mapNotNull { it.owned }
        return CollectionPlan(
            slots = slots,
            ownedCount = orderedOwned.size,
            releasedTotal = statuses.count { it != SlotStatus.UPCOMING },
            upcomingCount = statuses.count { it == SlotStatus.UPCOMING },
            orderedOwned = orderedOwned,
            nextUp = orderedOwned.firstOrNull { !isFinished(it) },
            hasFullList = details != null
        )
    }

    /** "6 films · 1993–2022 · 2 in your library", degrading gracefully offline. */
    fun subtitle(plan: CollectionPlan): String {
        if (!plan.hasFullList) {
            val n = plan.ownedCount
            return "$n in your library"
        }
        val years = plan.slots.filter { it.status != SlotStatus.UPCOMING }.mapNotNull { it.year }
        val span = when {
            years.isEmpty() -> null
            years.first() == years.last() -> years.first()
            else -> "${years.first()}–${years.last()}"
        }
        val films = "${plan.releasedTotal} film${if (plan.releasedTotal == 1) "" else "s"}"
        return listOfNotNull(films, span, "${plan.ownedCount} in your library").joinToString(" · ")
    }

    /** Short status for the page's top-right chip. */
    fun statusLabel(fetchedAtMs: Long?, isStale: Boolean, nowMs: Long, fetchEnabled: Boolean): String {
        if (fetchedAtMs == null) return if (fetchEnabled) "Owned films only" else "Online lookups off"
        val days = ((nowMs - fetchedAtMs) / 86_400_000L).coerceAtLeast(0L)
        return when {
            isStale -> "Offline copy · ${days}d old"
            days == 0L -> "Updated today"
            else -> "Updated ${days}d ago"
        }
    }

    private fun isReleased(releaseDate: String?, todayIso: String): Boolean =
        !releaseDate.isNullOrBlank() && releaseDate <= todayIso
}
