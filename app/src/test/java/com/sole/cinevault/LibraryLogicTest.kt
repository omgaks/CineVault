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
