package com.sole.cinevault.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameCleanerTest {
    @Test
    fun numberLedTitleKeepsTitleNumberAndUsesReleaseYear() {
        val filename = "2001.A.Space.Odyssey.1968.1080p.BluRay.mkv"
        assertEquals("2001 A Space Odyssey", tmdbMovieSearchQuery(filename))
        assertEquals("1968", extractYearHint(filename))
    }

    @Test
    fun sequelNumberIsNotMistakenForReleaseYear() {
        val filename = "Blade.Runner.2049.2017.2160p.mkv"
        assertEquals("Blade Runner 2049", tmdbMovieSearchQuery(filename))
        assertEquals("2017", extractYearHint(filename))
    }

    @Test
    fun singleLeadingYearRemainsPartOfTitle() {
        assertEquals("1917", tmdbMovieSearchQuery("1917.mkv"))
        assertNull(extractYearHint("1917.mkv"))
    }
}
