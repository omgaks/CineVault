package com.sole.cinevault.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarArtworkTest {
    @Test fun specificPosterBeatsGeneric() {
        val names = SidecarNames.posterNames("Heat.1995.mkv", allowGeneric = true)
        assertEquals("Heat.1995-poster.jpg", SidecarNames.pick(listOf("folder.jpg", "Heat.1995-poster.jpg"), names))
    }

    @Test fun genericOnlyWhenAllowed() {
        val files = listOf("folder.jpg", "poster.png")
        assertNull(SidecarNames.pick(files, SidecarNames.posterNames("Heat.mkv", allowGeneric = false)))
        assertEquals("poster.png", SidecarNames.pick(files, SidecarNames.posterNames("Heat.mkv", allowGeneric = true)))
    }

    @Test fun matchingIsCaseInsensitiveAndKeepsRealName() {
        assertEquals("FOLDER.JPG", SidecarNames.pick(listOf("FOLDER.JPG"), SidecarNames.posterNames("x.mkv", true)))
    }

    @Test fun backdropNames() {
        val files = listOf("Heat-fanart.jpg", "poster.jpg")
        assertEquals("Heat-fanart.jpg", SidecarNames.pick(files, SidecarNames.backdropNames("Heat.mkv", false)))
    }

    @Test fun primaryPathMaps() {
        val loc = safLocationOf("/storage/emulated/0/Movies/Jurassic/Jurassic Park.mkv")!!
        assertEquals("primary", loc.volume)
        assertEquals("Movies/Jurassic", loc.dir)
        assertEquals("Jurassic Park.mkv", loc.fileName)
        assertEquals("Movies", loc.parentDir)
    }

    @Test fun sdCardAndSdcardAlias() {
        assertEquals("ABCD-1234", safLocationOf("/storage/ABCD-1234/Films/a.mkv")!!.volume)
        assertEquals("primary", safLocationOf("/sdcard/Movies/a.mkv")!!.volume)
    }

    @Test fun nonFilesystemPathsAreNull() {
        assertNull(safLocationOf("content://media/external/video/media/12"))
        assertNull(safLocationOf("smb://host/share/a.mkv"))
        assertNull(safLocationOf("https://x/a.mp4"))
    }

    @Test fun fileAtVolumeRootHasEmptyDir() {
        val loc = safLocationOf("/storage/emulated/0/a.mkv")!!
        assertEquals("", loc.dir)
        assertNull(loc.parentDir)
    }

    @Test fun treeCoverage() {
        assertTrue(treeCovers("primary:Movies", "primary", "Movies"))
        assertTrue(treeCovers("primary:Movies", "primary", "Movies/Jurassic"))
        assertFalse(treeCovers("primary:Movies", "primary", "MoviesExtra"))
        assertFalse(treeCovers("primary:Movies", "ABCD-1234", "Movies"))
        assertTrue(treeCovers("primary:", "primary", "Anything/Deep"))
    }

    @Test fun nfoIds() {
        assertEquals(329, parseNfoTmdbId("<movie><tmdbid>329</tmdbid></movie>"))
        assertEquals(329, parseNfoTmdbId("<uniqueid default=\"true\" type=\"tmdb\">329</uniqueid>"))
        assertEquals(329, parseNfoTmdbId("https://www.themoviedb.org/movie/329-jurassic-park"))
        assertNull(parseNfoTmdbId("<movie><title>No ids</title></movie>"))
        assertNull(parseNfoTmdbId("<tmdbid>0</tmdbid>"))
    }
}
