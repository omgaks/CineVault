package com.sole.cinevault.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun bareEAndFanSubTagsAreRecognised() {
        val info = extractEpisodeInfo("Dawang Raoming - E04 (Sub).mp4")
        assertNotNull(info)
        assertEquals("Dawang Raoming", info!!.showName)
        assertEquals(1, info.season)
        assertEquals(4, info.episode)
        assertEquals(5, extractEpisodeInfo("Dawang Raoming - E05 (Sub).mp4")!!.episode)
    }

    @Test
    fun dashNumberedFanSubNamesAreEpisodes() {
        val info = extractEpisodeInfo("[SubsPlease] Some Show - 05 (1080p).mkv")
        assertNotNull(info)
        assertEquals("Some Show", info!!.showName)
        assertEquals(5, info.episode)
        assertEquals(7, extractEpisodeInfo("Some Show - 07v2 [720p].mkv")!!.episode)
    }

    @Test
    fun filmsWithDashesOrLetterEAreNotShows() {
        assertNull(extractEpisodeInfo("Catch-22.mkv"))
        assertNull(extractEpisodeInfo("Mission - 13.mkv"))
        assertNull(extractEpisodeInfo("E.T. The Extra-Terrestrial 1982.mkv"))
        assertNull(extractEpisodeInfo("Apollo 13 1995.mkv"))
    }

    @Test
    fun filmsNamedWithMovieWordAndEpisodeNumberAreNotShows() {
        assertNull(extractEpisodeInfo("Chainsaw Man The Movie Reze Arc E01.mkv"))
        assertNull(extractEpisodeInfo("Some Film E01 1080p.mkv"))
        assertNotNull(extractEpisodeInfo("Chainsaw Man S01E01.mkv"))
    }

    @Test
    fun explicitAndBareMarkersAreToldApart() {
        assertTrue(isExplicitSeasonEpisodeName("Show S02E05.mkv"))
        assertTrue(isExplicitSeasonEpisodeName("Show 2x05.mkv"))
        assertFalse(isExplicitSeasonEpisodeName("Show E05.mkv"))
        assertTrue(hasBareEpisodeMarker("Show E05.mkv"))
        assertFalse(hasBareEpisodeMarker("Show S01E05.mkv"))
        assertFalse(hasBareEpisodeMarker("Dune 2021.mkv"))
    }
}
