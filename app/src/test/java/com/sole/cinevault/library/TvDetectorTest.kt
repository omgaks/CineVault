package com.sole.cinevault.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TvDetectorTest {
    @Test
    fun seasonEpisodeStillWorks() {
        val info = extractEpisodeInfo("Breaking.Bad.S02E05.1080p.mkv")
        assertNotNull(info)
        assertEquals("Breaking Bad", info!!.showName)
        assertEquals(2, info.season)
        assertEquals(5, info.episode)
    }

    @Test
    fun plainEpisodeNumberIsSeasonOne() {
        val info = extractEpisodeInfo("Cyberpunk Edgerunners Episode 06.mkv")
        assertNotNull(info)
        assertEquals("Cyberpunk Edgerunners", info!!.showName)
        assertEquals(1, info.season)
        assertEquals(6, info.episode)
    }

    @Test
    fun releaseTagsAfterEpisodeAreIgnored() {
        val info = extractEpisodeInfo("Some Show Ep 12 1080p x265.mkv")
        assertNotNull(info)
        assertEquals("Some Show", info!!.showName)
        assertEquals(12, info.episode)
    }

    @Test
    fun filmWithEpisodeInTitleIsNotAShow() {
        assertNull(extractEpisodeInfo("Star Wars Episode 1 The Phantom Menace.mkv"))
        assertNull(extractEpisodeInfo("Dune 2021.mkv"))
    }
}
