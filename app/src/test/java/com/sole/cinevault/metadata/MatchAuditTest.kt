package com.sole.cinevault.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchAuditTest {
    private fun audit(file: String, title: String, sub: String, type: String = "movie", id: Int? = 1, check: Boolean = true) =
        auditMatch(file, type, id, title, sub, check).health

    @Test fun cleanMatchIsOk() {
        assertEquals(MatchHealth.OK, audit("Inception.2010.1080p.BluRay.mkv", "Inception", "2010"))
    }

    @Test fun noIdIsUnmatched() {
        assertEquals(MatchHealth.UNMATCHED, audit("Whatever.mkv", "Whatever", "", id = null))
    }

    @Test fun differentTitleIsDoubtful() {
        assertEquals(MatchHealth.DOUBTFUL, audit("Alien.1979.mkv", "Space Cowboys", "1979"))
    }

    @Test fun yearFarApartIsDoubtful() {
        assertEquals(MatchHealth.DOUBTFUL, audit("Jurassic.Park.1993.mkv", "Jurassic Park", "2001"))
    }

    @Test fun oneYearOffIsFine() {
        assertEquals(MatchHealth.OK, audit("Heat.1995.mkv", "Heat", "1996"))
    }

    @Test fun titleContainedIsOk() {
        assertEquals(MatchHealth.OK, audit("Aliens.Special.Edition.mkv", "Aliens", "1986"))
    }

    @Test fun nonEnglishMetadataSkipsTitleCheck() {
        assertEquals(MatchHealth.OK, audit("Parasite.2019.mkv", "Gisaengchung", "2019", check = false))
    }

    @Test fun nonLatinMatchedTitleIsNotJudged() {
        assertEquals(MatchHealth.OK, audit("Parasite.2019.mkv", "寄生虫", "2019"))
    }

    @Test fun otherTypesAreIgnored() {
        assertEquals(MatchHealth.OK, audit("x.mp4", "Something", "", type = "local", id = null))
    }

    @Test fun tvUsesShowName() {
        assertEquals(MatchHealth.OK, audit("Breaking.Bad.S01E01.mkv", "Breaking Bad", "", type = "tv"))
        assertEquals(MatchHealth.DOUBTFUL, audit("Breaking.Bad.S01E01.mkv", "The Office", "", type = "tv"))
    }

    @Test fun similarityBounds() {
        assertEquals(0f, titleSimilarity("abc def", "xyz uvw"), 0f)
        assertTrue(titleSimilarity("The Matrix", "Matrix") >= 0.75f)
    }
}
