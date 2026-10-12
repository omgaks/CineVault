package com.sole.cinevault.collections

/**
 * Pure rules for the franchise strip on a film's detail page.
 * The strip answers "what else is in this franchise, and which of it do I own?"
 */
object FranchiseStripLogic {

    /** The strip needs a franchise: two or more films in total, owned or not. */
    fun shouldShow(plan: CollectionPlan): Boolean = plan.slots.size >= 2

    /** Position of the film being viewed inside the strip, or -1 when it is not listed. */
    fun currentIndex(plan: CollectionPlan, tmdbId: Int?): Int =
        if (tmdbId == null) -1 else plan.slots.indexOfFirst { it.part.tmdbId == tmdbId }

    /** "The Incredibles Collection · 1 of 2 in your library". */
    fun header(name: String, plan: CollectionPlan): String {
        val clean = name.trim().ifBlank { "Collection" }
        val total = plan.releasedTotal
        return if (!plan.hasFullList || total <= 0) "$clean · ${plan.ownedCount} in your library"
        else "$clean · ${plan.ownedCount.coerceAtMost(total)} of $total in your library"
    }
}
