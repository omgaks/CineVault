package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {
    @Test
    fun removedCategoriesFallBackToAll() {
        assertEquals("All", normalizeLibraryCategory("Downloads"))
        assertEquals("Movies", normalizeLibraryCategory("Movies"))
        assertFalse("Downloads" in LIBRARY_CATEGORIES)
        assertEquals("All", normalizeLibraryCategory("Duplicates"))
    }

    @Test
    fun onlyRefreshIgnoresOutsideTaps() {
        LibraryPanel.values().forEach { panel ->
            assertEquals(panel != LibraryPanel.Refresh, panelClosesOnOutsideTap(panel))
        }
    }

    @Test
    fun choosingClosesPickerPanelsButNotScanOrRefresh() {
        assertTrue(panelClosesAfterChoice(LibraryPanel.Sort))
        assertTrue(panelClosesAfterChoice(LibraryPanel.Category))
        assertFalse(panelClosesAfterChoice(LibraryPanel.Scan))
        assertFalse(panelClosesAfterChoice(LibraryPanel.Refresh))
    }

    @Test
    fun yearIsFoundInFileNames() {
        assertEquals("2018", extractYearFromName("Avengers.Infinity.War.2018.1080p.mkv"))
        assertNull(extractYearFromName("holiday clip.mp4"))
    }

    @Test
    fun metaSkipsMissingParts() {
        assertEquals("2018 · Sci-Fi", joinMeta("2018", null, "Sci-Fi"))
        assertEquals("", joinMeta(null, "  "))
    }
}

class LibraryDuplicatesAndSearchTest {
    private fun copy(path: String, name: String, size: Long) = CopyInfo(path, name, size)

    @Test
    fun betterQualityCopyIsTheMainOne() {
        val a = copy("/a/Dune.2021.720p.mkv", "Dune.2021.720p.mkv", 2_000)
        val b = copy("/b/Dune.2021.2160p.HDR.mkv", "Dune.2021.2160p.HDR.mkv", 9_000)
        assertEquals(b.path, chooseMainCopy(listOf(a, b), emptySet()).path)
    }

    @Test
    fun sameQualityPrefersTheBiggerFile() {
        val a = copy("/a/x.1080p.mkv", "x.1080p.mkv", 3_000)
        val b = copy("/b/x.1080p.mkv", "x.1080p.mkv", 5_000)
        assertEquals(b.path, chooseMainCopy(listOf(a, b), emptySet()).path)
    }

    @Test
    fun aChosenCopyWinsOverQuality() {
        val a = copy("/a/x.720p.mkv", "x.720p.mkv", 1_000)
        val b = copy("/b/x.2160p.mkv", "x.2160p.mkv", 9_000)
        assertEquals(a.path, chooseMainCopy(listOf(a, b), setOf(a.path)).path)
    }

    @Test
    fun foldHidesOnlyTheOtherCopies() {
        val a = copy("/a/x.720p.mkv", "x.720p.mkv", 1_000)
        val b = copy("/b/x.1080p.mkv", "x.1080p.mkv", 2_000)
        val c = copy("/c/solo.mkv", "solo.mkv", 500)
        val plan = planDuplicateFold(listOf(listOf(a, b), listOf(c)), emptySet())
        assertEquals(setOf(a.path), plan.hiddenPaths)
        assertEquals(listOf(a.path, b.path), plan.groupsByMain[b.path])
        assertFalse(plan.groupsByMain.containsKey(c.path))
    }

    @Test
    fun choosingAMainCopyReplacesTheOldChoiceInThatGroup() {
        val updated = withMainCopy(setOf("/a", "/z"), "/b", listOf("/a", "/b"))
        assertEquals(setOf("/b", "/z"), updated)
    }

    private val doc = SearchDoc(
        title = "Avengers: Infinity War",
        fileName = "Avengers.Infinity.War.2018.mkv",
        cast = listOf("Robert Downey Jr.", "Chris Evans"),
        director = "Anthony Russo",
        genres = listOf("Action", "Sci-Fi"),
        year = "2018"
    )

    @Test
    fun searchReportsWhereItMatched() {
        assertEquals(SearchMatch.Title, searchDocument(doc, "infinity")?.matchedBy)
        assertEquals(SearchMatch.Cast, searchDocument(doc, "downey")?.matchedBy)
        assertEquals(SearchMatch.Director, searchDocument(doc, "russo")?.matchedBy)
        assertEquals(SearchMatch.Genre, searchDocument(doc, "sci-fi")?.matchedBy)
        assertEquals(SearchMatch.Year, searchDocument(doc, "2018")?.matchedBy)
        assertNull(searchDocument(doc, "zzz"))
        assertNull(searchDocument(doc, "   "))
    }

    @Test
    fun everyWordMustMatchAndTitleStartRanksFirst() {
        assertNull(searchDocument(doc, "infinity zzz"))
        assertEquals(SearchMatch.Title, searchDocument(doc, "infinity war")?.matchedBy)
        val start = searchDocument(doc, "avengers")!!.rank
        val inside = searchDocument(doc, "war")!!.rank
        assertTrue(start < inside)
    }

    @Test
    fun filtersCombineAndIgnoreWhenOff() {
        val f = SearchFilters(rating7 = true, unwatched = true)
        assertTrue(passesSearchFilters(f, true, 7.5, "a.mkv", watched = false, favourite = false))
        assertFalse(passesSearchFilters(f, true, 6.9, "a.mkv", watched = false, favourite = false))
        assertFalse(passesSearchFilters(f, true, 8.0, "a.mkv", watched = true, favourite = false))
        assertTrue(passesSearchFilters(SearchFilters(), false, null, "a.mkv", true, false))
        assertTrue(passesSearchFilters(SearchFilters(fourKOrHdr = true), true, null, "A.2160p.mkv", false, false))
        assertFalse(passesSearchFilters(SearchFilters(fourKOrHdr = true), true, null, "A.720p.mkv", false, false))
    }

    @Test
    fun recentsAreNewestFirstWithoutRepeats() {
        var r = emptyList<String>()
        r = pushRecentSearch(r, "dune")
        r = pushRecentSearch(r, "thor")
        r = pushRecentSearch(r, "DUNE")
        assertEquals(listOf("DUNE", "thor"), r)
        assertEquals(r, pushRecentSearch(r, "  "))
        assertEquals(6, (1..10).fold(emptyList<String>()) { acc, i -> pushRecentSearch(acc, "q$i") }.size)
    }
}
