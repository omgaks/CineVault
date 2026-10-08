package com.sole.cinevault.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkHistoryPolicyTest {
    @Test fun remembersTheReplacedUrl() {
        assertEquals("a", urlToRemember("a", "b"))
        assertEquals("a", urlToRemember("a", null))
    }

    @Test fun ignoresNoChangeAndEmpty() {
        assertNull(urlToRemember("a", "a"))
        assertNull(urlToRemember(null, "b"))
        assertNull(urlToRemember("  ", "b"))
    }

    @Test fun tidyDedupesExcludesAndLimits() {
        val out = tidyHistory(listOf("a", "b", "a", "c", "d"), exclude = setOf("b"), limit = 2)
        assertEquals(listOf("a", "c"), out)
    }
}
