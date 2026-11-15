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

    @Test
    fun episodeNumbersPastNinetyNineAreRecognised() {
        val hundred = extractEpisodeInfo("One.Piece.S01E100.1080p.mkv")
        assertNotNull(hundred)
        assertEquals("One Piece", hundred!!.showName)
        assertEquals(1, hundred.season)
        assertEquals(100, hundred.episode)
        assertEquals(123, extractEpisodeInfo("Naruto S05E123.mkv")!!.episode)
        assertEquals(245, extractEpisodeInfo("Soul Land Episode 245.mp4")!!.episode)
        assertEquals(1071, extractEpisodeInfo("One Piece Episode 1071.mkv")!!.episode)
        assertTrue(isExplicitSeasonEpisodeName("One.Piece.S01E100.1080p.mkv"))
    }

    @Test
    fun fourDigitFanSubNumbersAreEpisodesButYearsAreNot() {
        val info = extractEpisodeInfo("[SubsPlease] One Piece - 1071 (1080p) [ABCD1234].mkv")
        assertNotNull(info)
        assertEquals("One Piece", info!!.showName)
        assertEquals(1071, info.episode)
        assertNull(extractEpisodeInfo("[Group] Some Film - 2019 (1080p).mkv"))
    }

    @Test
    fun doubleEpisodesCountAsTheFirstOne() {
        val joined = extractEpisodeInfo("Show.S01E01E02.mkv")
        assertNotNull(joined)
        assertEquals("Show", joined!!.showName)
        assertEquals(1, joined.season)
        assertEquals(1, joined.episode)
        assertEquals(3, extractEpisodeInfo("Show.S02E03-E04.720p.mkv")!!.episode)
    }

    @Test
    fun spacedAndSpelledOutSeasonMarkersKeepTheShowNameClean() {
        val spaced = extractEpisodeInfo("The Office - S02 - E05.mkv")
        assertNotNull(spaced)
        assertEquals("The Office", spaced!!.showName)
        assertEquals(2, spaced.season)
        assertEquals(5, spaced.episode)
        val words = extractEpisodeInfo("Breaking Bad Season 1 Episode 3.mkv")
        assertNotNull(words)
        assertEquals("Breaking Bad", words!!.showName)
        assertEquals(1, words.season)
        assertEquals(3, words.episode)
        assertTrue(isExplicitSeasonEpisodeName("Breaking Bad Season 1 Episode 3.mkv"))
        assertFalse(hasBareEpisodeMarker("Breaking Bad Season 1 Episode 3.mkv"))
    }

    @Test
    fun chineseEpisodeMarkersAreRecognised() {
        val info = extractEpisodeInfo("\u6597\u7834\u82cd\u7a79 \u7b2c01\u96c6.mp4")
        assertNotNull(info)
        assertEquals("\u6597\u7834\u82cd\u7a79", info!!.showName)
        assertEquals(1, info.season)
        assertEquals(1, info.episode)
        val tagged = extractEpisodeInfo("Throne of Seal \u7b2c150\u96c6 4K.mp4")
        assertEquals("Throne of Seal", tagged!!.showName)
        assertEquals(150, tagged.episode)
        val season = extractEpisodeInfo("Soul Land \u7b2c2\u5b63 \u7b2c12\u8bdd.mp4")
        assertEquals(2, season!!.season)
        assertEquals(12, season.episode)
        assertTrue(isExplicitSeasonEpisodeName("Throne of Seal \u7b2c150\u96c6 4K.mp4"))
        // A marker with no show name in front of it cannot be filed under a show.
        assertNull(extractEpisodeInfo("\u7b2c01\u96c6.mp4"))
    }

    @Test
    fun resolutionsAndYearsAreNotEpisodeMarkers() {
        assertNull(extractEpisodeInfo("Some Film 1920x1080.mkv"))
        assertNull(extractEpisodeInfo("Blade Runner 2049 2017 1080p.mkv"))
        assertFalse(isExplicitSeasonEpisodeName("Some Film 1920x1080.mkv"))
    }
}
