package com.sole.cinevault.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRuleTest {
    @Test fun roundTrip() {
        val r = SmartRule(setOf("Horror", "Thriller"), 1980, 7.0, "John Carpenter")
        val back = SmartRule.decode(r.encode())!!
        assertEquals(r.genres, back.genres)
        assertEquals(1980, back.decade)
        assertEquals(7.0, back.minRating!!, 0.0)
        assertEquals("John Carpenter", back.director)
    }

    @Test fun emptyOrJunkDecodesToNull() {
        assertNull(SmartRule.decode(null))
        assertNull(SmartRule.decode(""))
        assertNull(SmartRule.decode("nonsense"))
        assertNull(SmartRule.decode("decade=abc"))
    }

    @Test fun emptyRuleMatchesNothing() {
        assertFalse(SmartRule().matches(listOf("Horror"), 1980, 9.0, "x"))
    }

    @Test fun genreIsAnyOfAndCaseInsensitive() {
        val r = SmartRule(genres = setOf("horror"))
        assertTrue(r.matches(listOf("Drama", "Horror"), null, null, null))
        assertFalse(r.matches(listOf("Drama"), null, null, null))
    }

    @Test fun decadeBounds() {
        val r = SmartRule(decade = 1980)
        assertTrue(r.matches(emptyList(), 1980, null, null))
        assertTrue(r.matches(emptyList(), 1989, null, null))
        assertFalse(r.matches(emptyList(), 1990, null, null))
        assertFalse(r.matches(emptyList(), null, null, null))
    }

    @Test fun ratingAndDirectorAreAnded() {
        val r = SmartRule(minRating = 7.5, director = "nolan")
        assertTrue(r.matches(emptyList(), null, 8.0, "Christopher Nolan"))
        assertFalse(r.matches(emptyList(), null, 7.0, "Christopher Nolan"))
        assertFalse(r.matches(emptyList(), null, 8.0, "Someone Else"))
        assertFalse(r.matches(emptyList(), null, null, "Christopher Nolan"))
    }

    @Test fun separatorsInValuesCannotBreakEncoding() {
        val r = SmartRule(genres = setOf("Sci;Fi=Odd|Name"))
        val back = SmartRule.decode(r.encode())!!
        assertEquals(1, back.genres.size)
    }

    @Test fun describeIsReadable() {
        assertEquals("Horror  ·  1980s  ·  7+ rating", SmartRule(setOf("Horror"), 1980, 7.0).describe())
    }
}
