package com.sole.cinevault.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowMatchesTest {
    @Test
    fun showKeyIgnoresCaseSpacingAndPunctuation() {
        assertEquals("dawangraoming", showKey("Dawang Raoming"))
        assertEquals("dawangraoming", showKey("Da Wang Rao-Ming"))
    }

    @Test
    fun knownAlternativeTitlesFollowTheFilenameSpelling() {
        val queries = showSearchQueries("Dawang Raoming")
        assertEquals("Dawang Raoming", queries.first())
        assertTrue(queries.contains("Spare Me, Great Lord!"))
        assertTrue(queries.contains("大王饶命"))
    }

    @Test
    fun unknownShowsOnlyTryTheirOwnName() {
        assertEquals(listOf("Some Show"), showSearchQueries("Some Show"))
    }
}
