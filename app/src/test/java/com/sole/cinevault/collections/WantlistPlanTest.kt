package com.sole.cinevault.collections

import org.junit.Assert.assertEquals
import org.junit.Test

class WantlistPlanTest {
    private fun film(id: Int, title: String, date: String?, added: Long = 0L) =
        WantItem(id, title, null, date, null, added)

    private val today = "2026-10-09"

    @Test fun statusAndLabels() {
        val rows = WantlistPlanner.plan(
            listOf(
                film(1, "Out", "2001-05-01"),
                film(2, "Soon", "2027-06-12"),
                film(3, "Mine", "1999-01-01"),
                film(4, "Unknown", null),
            ),
            ownedMovieIds = setOf(3), todayIso = today
        )
        val byId = rows.associateBy { it.film.tmdbId }
        assertEquals(WantStatus.AVAILABLE, byId[1]!!.status)
        assertEquals("Out now · 2001", byId[1]!!.label)
        assertEquals(WantStatus.UPCOMING, byId[2]!!.status)
        assertEquals("Coming 12 Jun 2027", byId[2]!!.label)
        assertEquals(WantStatus.OWNED, byId[3]!!.status)
        assertEquals("Date to be announced", byId[4]!!.label)
    }

    @Test fun orderAvailableThenUpcomingThenOwned() {
        val rows = WantlistPlanner.plan(
            listOf(
                film(1, "Owned", "2000-01-01"),
                film(2, "Later", "2028-01-01"),
                film(3, "Sooner", "2027-01-01"),
                film(4, "Newest add", "2002-01-01", added = 200),
                film(5, "Older add", "2003-01-01", added = 100),
            ),
            ownedMovieIds = setOf(1), todayIso = today
        )
        assertEquals(listOf(4, 5, 3, 2, 1), rows.map { it.film.tmdbId })
    }

    @Test fun summaryWords() {
        assertEquals("Nothing on your wantlist yet.", WantlistPlanner.summary(emptyList()))
        val rows = WantlistPlanner.plan(listOf(film(1, "A", "2001-01-01"), film(2, "B", "2030-01-01")), emptySet(), today)
        assertEquals("1 to find  ·  1 coming", WantlistPlanner.summary(rows))
    }

    @Test fun prettyDateHandlesJunk() {
        assertEquals("5 Jan 2027", WantlistPlanner.prettyDate("2027-01-05"))
        assertEquals("Jan 2027", WantlistPlanner.prettyDate("2027-01"))
        assertEquals("soon", WantlistPlanner.prettyDate("soon"))
    }
}
