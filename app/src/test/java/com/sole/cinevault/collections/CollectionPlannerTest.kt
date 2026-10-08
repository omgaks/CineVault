package com.sole.cinevault.collections

import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPlannerTest {
    private val today = "2026-10-08"

    private fun video(id: Int?, name: String, collectionId: Int? = 328) = VideoWithMetadata(
        video = VideoFile(name = "$name.mkv", path = "/m/$name.mkv"),
        title = name, subtitle = "", posterUrl = null, backdropUrl = null,
        overview = null, rating = null, tmdbId = id, type = "movie",
        collectionId = collectionId, collectionName = "Jurassic"
    )

    private val details = CollectionDetails(
        id = 328, name = "Jurassic Collection", overview = null, posterPath = null, backdropPath = null,
        parts = listOf(
            CollectionPart(4, "Jurassic World", "2015-06-06", "/a", null),
            CollectionPart(1, "Jurassic Park", "1993-06-11", "/b", null),
            CollectionPart(3, "Jurassic Park III", "2001-07-18", "/c", null),
            CollectionPart(2, "The Lost World", "1997-05-23", "/d", null),
            CollectionPart(9, "Future Film", "2099-01-01", "/e", null),
            CollectionPart(8, "Undated Film", "", null, null)
        )
    )
    private val owned = listOf(video(1, "JP"), video(3, "JP3"))

    @Test fun ordersByReleaseDateWithUpcomingLast() {
        val plan = CollectionPlanner.plan(details, owned, today) { false }
        assertEquals(listOf(1, 2, 3, 4, 9, 8), plan.slots.map { it.part.tmdbId })
        assertEquals(4, plan.releasedTotal)
        assertEquals(2, plan.upcomingCount)
    }

    @Test fun marksTheGapBetweenOwnedFilms() {
        val slot = CollectionPlanner.plan(details, owned, today) { false }.slots[1]
        assertEquals(SlotStatus.MISSING, slot.status)
        assertTrue(slot.isGap)
        assertEquals(1, slot.gapAfter)
        assertEquals(3, slot.gapBefore)
    }

    @Test fun missingFilmAfterLastOwnedIsNotAGap() {
        val slot = CollectionPlanner.plan(details, owned, today) { false }.slots[3]
        assertEquals(SlotStatus.MISSING, slot.status)
        assertFalse(slot.isGap)
    }

    @Test fun futureAndUndatedFilmsAreUpcomingAndUnnumbered() {
        val slots = CollectionPlanner.plan(details, owned, today) { false }.slots
        assertEquals(SlotStatus.UPCOMING, slots[4].status)
        assertNull(slots[4].number)
        assertEquals(SlotStatus.UPCOMING, slots[5].status)
    }

    @Test fun nextUpIsFirstUnfinishedOwnedFilm() {
        assertEquals(1, CollectionPlanner.plan(details, owned, today) { false }.nextUp?.tmdbId)
        assertEquals(3, CollectionPlanner.plan(details, owned, today) { it.tmdbId == 1 }.nextUp?.tmdbId)
        assertNull(CollectionPlanner.plan(details, owned, today) { true }.nextUp)
    }

    @Test fun duplicateFilesOfOneFilmCountOnce() {
        val plan = CollectionPlanner.plan(details, owned + video(1, "JP-4K"), today) { false }
        assertEquals(2, plan.ownedCount)
        assertEquals(mapOf(328 to 2), CollectionPlanner.ownedCountsByCollection(owned + video(1, "JP-4K") + video(5, "x", null)))
    }

    @Test fun ownedFilmMissingFromTmdbListIsKept() {
        val plan = CollectionPlanner.plan(details, owned + video(77, "Rebirth"), today) { false }
        assertTrue(plan.slots.any { it.part.tmdbId == 77 && it.status == SlotStatus.OWNED })
    }

    @Test fun withoutDetailsFallsBackToOwnedOnly() {
        val plan = CollectionPlanner.plan(null, owned, today) { false }
        assertFalse(plan.hasFullList)
        assertEquals(2, plan.slots.size)
        assertEquals("2 in your library", CollectionPlanner.subtitle(plan))
    }

    @Test fun completeWhenEveryReleasedFilmIsOwned() {
        val all = (1..4).map { video(it, "F$it") }
        val plan = CollectionPlanner.plan(details, all, today) { true }
        assertTrue(plan.isComplete)
        assertEquals(1f, plan.progress, 0.001f)
    }

    @Test fun shelfRuleNeedsTwoOwnedFilms() {
        assertFalse(CollectionPlanner.earnsShelfCard(1))
        assertTrue(CollectionPlanner.earnsShelfCard(2))
    }

    @Test fun subtitleSummarisesFilmsYearsAndOwned() {
        val plan = CollectionPlanner.plan(details, owned, today) { false }
        assertEquals("4 films · 1993–2015 · 2 in your library", CollectionPlanner.subtitle(plan))
    }

    @Test fun statusLabelCoversFreshStaleAndOffline() {
        assertEquals("Owned films only", CollectionPlanner.statusLabel(null, false, 0, true))
        assertEquals("Online lookups off", CollectionPlanner.statusLabel(null, false, 0, false))
        assertEquals("Updated today", CollectionPlanner.statusLabel(0, false, 10_000, true))
        assertEquals("Offline copy · 3d old", CollectionPlanner.statusLabel(0, true, 3 * 86_400_000L, true))
    }
}
