package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    @Test
    fun greetingFollowsTimeOfDayAndName() {
        assertEquals("Good morning, Ash.", homeGreeting(8, "Ash"))
        assertEquals("Good afternoon, Ash.", homeGreeting(14, "Ash"))
        assertEquals("Good evening, Ash.", homeGreeting(19, "Ash"))
        assertEquals("Good evening.", homeGreeting(19, ""))
        assertEquals("Good evening.", homeGreeting(19, "   "))
    }

    @Test
    fun lateNightAsksIfStillUp() {
        assertEquals("Still up, Ash?", homeGreeting(22, "Ash"))
        assertEquals("Still up, Ash?", homeGreeting(2, "Ash"))
        assertEquals("Still up?", homeGreeting(23, ""))
        assertEquals("Good morning.", homeGreeting(5, ""))
    }

    @Test
    fun dayPartLabels() {
        assertEquals("MORNING", homeDayPart(9))
        assertEquals("AFTERNOON", homeDayPart(13))
        assertEquals("EVENING", homeDayPart(18))
        assertEquals("NIGHT", homeDayPart(23))
    }

    @Test
    fun budgetFormatting() {
        assertEquals("2H", formatBudgetMinutes(120))
        assertEquals("2H 30M", formatBudgetMinutes(150))
        assertEquals("45M", formatBudgetMinutes(45))
        assertEquals("0M", formatBudgetMinutes(-5))
    }

    @Test
    fun gaugeFractionIsClamped() {
        assertEquals(0f, tonightGaugeFraction(30), 0.0001f)
        assertEquals(0f, tonightGaugeFraction(60), 0.0001f)
        assertEquals(1f, tonightGaugeFraction(240), 0.0001f)
        assertEquals(1f, tonightGaugeFraction(500), 0.0001f)
        assertEquals(0.5f, tonightGaugeFraction(150), 0.0001f)
    }

    @Test
    fun fitsAreSortedBestFirstAndSkipUnknownAndTooLong() {
        val films = listOf("a" to 100, "b" to 118, "c" to 150, "d" to 0, "e" to 60)
        val fits = fitsWithin(films, 120) { it.second }
        assertEquals(listOf("b", "a", "e"), fits.map { it.first.first })
        assertEquals(2, fits.first().second)
        assertTrue(fitsWithin(films, 30) { it.second }.isEmpty())
    }

    @Test
    fun spareTextReadsNaturally() {
        assertEquals("Perfect fit", spareText(0))
        assertEquals("12 min to spare", spareText(12))
        assertEquals("1h 30m to spare", spareText(90))
    }
}
