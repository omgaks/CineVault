package com.sole.cinevault.collections

import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniverseStoryTest {
    private val today = "2026-10-09"

    private fun video(id: Int, name: String, cid: Int = 1, cname: String = "X") = VideoWithMetadata(
        video = VideoFile(name = "$name.mkv", path = "/m/$name.mkv"),
        title = name, subtitle = "", posterUrl = null, backdropUrl = null,
        overview = null, rating = null, tmdbId = id, type = "movie",
        collectionId = cid, collectionName = cname
    )

    private fun part(id: Int, title: String, date: String?) = CollectionPart(id, title, date, null, null)

    private val hobbit = CollectionDetails(121938, "The Hobbit Collection", null, null, "/h", listOf(
        part(70, "The Hobbit: An Unexpected Journey", "2012-12-12"),
        part(71, "The Hobbit: The Desolation of Smaug", "2013-12-11"),
        part(72, "The Hobbit: The Battle of the Five Armies", "2014-12-10")
    ))
    private val lotr = CollectionDetails(119, "The Lord of the Rings Collection", null, null, null, listOf(
        part(80, "The Fellowship of the Ring", "2001-12-18"),
        part(81, "The Two Towers", "2002-12-18"),
        part(82, "The Return of the King", "2003-12-17")
    ))
    private val middleEarth = UniverseCatalog.byId("middle-earth")!!

    @Test fun mergeDedupesPartsAndKeepsFirstArtwork() {
        val merged = CollectionPlanner.mergeDetails(119, "Middle-earth", listOf(lotr, hobbit, lotr))
        assertEquals(6, merged.parts.size)
        assertEquals("/h", merged.backdropPath)
    }

    @Test fun releaseOrderInterleavesTheTwoTrilogies() {
        val merged = CollectionPlanner.mergeDetails(119, "Middle-earth", listOf(lotr, hobbit))
        val plan = CollectionPlanner.plan(merged, emptyList(), today) { false }
        assertEquals(listOf(80, 81, 82, 70, 71, 72), plan.slots.map { it.part.tmdbId })
    }

    @Test fun storyOrderPutsTheHobbitFirst() {
        val merged = CollectionPlanner.mergeDetails(119, "Middle-earth", listOf(lotr, hobbit))
        val plan = CollectionPlanner.plan(merged, emptyList(), today, middleEarth.story) { false }
        assertEquals(listOf(70, 71, 72, 80, 81, 82), plan.slots.map { it.part.tmdbId })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), plan.slots.map { it.number })
    }

    @Test fun storyOrderGapsUseStoryNumbers() {
        val merged = CollectionPlanner.mergeDetails(119, "Middle-earth", listOf(lotr, hobbit))
        // own Hobbit 1 and Fellowship -> story positions 1 and 4; Hobbit 2/3 are the gap.
        val owned = listOf(video(70, "H1"), video(80, "F"))
        val plan = CollectionPlanner.plan(merged, owned, today, middleEarth.story) { false }
        val gap = plan.slots[1]
        assertTrue(gap.isGap)
        assertEquals(1, gap.gapAfter)
        assertEquals(4, gap.gapBefore)
        assertEquals(listOf(70, 80), plan.orderedOwned.map { it.tmdbId })
    }

    @Test fun filmsTheStoryDoesNotMentionFollowInReleaseOrderAndUpcomingStaysLast() {
        val sw = CollectionDetails(10, "Star Wars Collection", null, null, null, listOf(
            part(1, "Star Wars", "1977-05-25"),
            part(2, "The Phantom Menace", "1999-05-19"),
            part(3, "The Clone Wars", "2008-08-15"),
            part(4, "Future Saga Film", "2099-01-01")
        ))
        val story = UniverseCatalog.byId("star-wars")!!.story
        val ids = CollectionPlanner.plan(sw, emptyList(), today, story) { false }.slots.map { it.part.tmdbId }
        assertEquals(listOf(2, 1, 3, 4), ids)
    }

    @Test fun aStoryYearThatMatchesNothingIsIgnored() {
        val d = CollectionDetails(1, "X", null, null, null, listOf(part(1, "A", "2001-01-01"), part(2, "B", "2002-01-01")))
        val ids = CollectionPlanner.plan(d, emptyList(), today, listOf(StoryKey(1850), StoryKey(2002))) { false }.slots.map { it.part.tmdbId }
        assertEquals(listOf(2, 1), ids)
    }

    @Test fun titleHintBreaksASameYearTie() {
        val d = CollectionDetails(1, "X", null, null, null, listOf(part(1, "Alpha", "2010-01-01"), part(2, "Beta", "2010-06-01")))
        val ids = CollectionPlanner.plan(d, emptyList(), today, listOf(StoryKey(2010, "beta"), StoryKey(2010, "alpha"))) { false }.slots.map { it.part.tmdbId }
        assertEquals(listOf(2, 1), ids)
    }

    @Test fun subtitleYearSpanIgnoresDisplayOrder() {
        val merged = CollectionPlanner.mergeDetails(119, "Middle-earth", listOf(lotr, hobbit))
        val plan = CollectionPlanner.plan(merged, emptyList(), today, middleEarth.story) { false }
        assertEquals("6 films · 2001–2014 · 0 in your library", CollectionPlanner.subtitle(plan))
    }

    @Test fun collectionNamesMatchDespiteNamingDrift() {
        assertEquals("lord of the rings", UniverseCatalog.key("The Lord of the Rings Collection"))
        assertEquals(UniverseCatalog.key("Fast & Furious Collection"), UniverseCatalog.key("  fast  & furious collection "))
        assertTrue(middleEarth.ownsCollectionName("the hobbit collection"))
        assertFalse(middleEarth.ownsCollectionName("Star Wars Collection"))
        assertFalse(middleEarth.ownsCollectionName(null))
    }

    @Test fun catalogLooksUpAUniverseByMemberCollectionAndHonoursTheSwitch() {
        UniverseCatalog.groupingEnabled = true
        assertEquals("jurassic", UniverseCatalog.forCollectionName("Jurassic World Collection")?.id)
        assertNull(UniverseCatalog.forCollectionName("Unknown Collection"))
        UniverseCatalog.groupingEnabled = false
        assertNull(UniverseCatalog.forCollectionName("Jurassic World Collection"))
        UniverseCatalog.groupingEnabled = true
    }

    @Test fun catalogIdsAreUniqueAndEveryStoryYearIsPlausible() {
        val ids = UniverseCatalog.universes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        UniverseCatalog.universes.forEach { u ->
            assertTrue(u.collections.isNotEmpty())
            assertEquals("duplicate story years in ${u.id}", u.story.size, u.story.map { it.year to it.hint }.toSet().size)
            u.story.forEach { assertTrue(it.year in 1900..2100) }
        }
    }

    @Test fun timelineShowsEachYearOnceAndMarksEnds() {
        val d = CollectionDetails(1, "X", null, null, null, listOf(
            part(1, "A", "2001-01-01"), part(2, "B", "2001-09-01"), part(3, "C", "2004-01-01"), part(4, "D", "2099-01-01")
        ))
        val rows = CollectionPlanner.timelineRows(CollectionPlanner.plan(d, emptyList(), today) { false })
        assertEquals(listOf("2001", null, "2004", "2099"), rows.map { it.yearLabel })
        assertTrue(rows.first().isFirst)
        assertTrue(rows.last().isLast)
        assertFalse(rows[1].isFirst || rows[1].isLast)
    }

    @Test fun marathonOnlyMatchesTheExactListItStartedWith() {
        MarathonSession.clear()
        assertFalse(MarathonSession.matches(listOf("a", "b")))
        MarathonSession.start(listOf("a", "b", "c"))
        assertTrue(MarathonSession.matches(listOf("a", "b", "c")))
        assertFalse(MarathonSession.matches(listOf("a", "c", "b")))
        assertFalse(MarathonSession.matches(listOf("a", "b")))
        MarathonSession.start(listOf("only"))
        assertFalse(MarathonSession.matches(listOf("only")))
        MarathonSession.clear()
        assertNotNull(MarathonSession)
    }
}
