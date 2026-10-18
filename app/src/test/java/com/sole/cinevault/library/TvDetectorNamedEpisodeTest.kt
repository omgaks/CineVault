package com.sole.cinevault.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvDetectorNamedEpisodeTest {
    @Test fun namedEpisodeWithTwoDigits() {
        val result = extractEpisodeInfo("Cyberpunk Edgerunners Episode 09.mkv")
        assertEquals("Cyberpunk Edgerunners", result?.showName)
        assertEquals(1, result?.season)
        assertEquals(9, result?.episode)
    }

    @Test fun standardSeasonEpisodeStillWorks() {
        val result = extractEpisodeInfo("Cyberpunk.Edgerunners.S01E10.mkv")
        assertEquals(1, result?.season)
        assertEquals(10, result?.episode)
    }

    @Test fun ordinaryMovieIsNotEpisode() {
        assertNull(extractEpisodeInfo("The Matrix 1999.mkv"))
    }
}
