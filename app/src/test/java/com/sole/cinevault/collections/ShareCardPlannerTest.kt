package com.sole.cinevault.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardPlannerTest {
    private fun e(t: String, y: String?, o: Boolean) = ShareEntry(t, y, o)

    @Test fun partialCollection() {
        val m = ShareCardPlanner.model("Jurassic", listOf(e("A", "1993", true), e("B", "1997", false)), 1, 2, true)
        assertEquals("I own 1 of 2", m.headline)
        assertEquals(0.5f, m.progress, 0.001f)
        assertEquals("A (1993)", m.lines[0].text)
    }

    @Test fun completeCollection() {
        val m = ShareCardPlanner.model("X", listOf(e("A", null, true), e("B", null, true)), 2, 2, true)
        assertEquals("Complete: all 2", m.headline)
        assertEquals(1f, m.progress, 0f)
        assertEquals("A", m.lines[0].text)
    }

    @Test fun withoutFullListOnlyOwnedAreShown() {
        val m = ShareCardPlanner.model("X", listOf(e("A", null, true), e("B", null, false)), 1, 0, false)
        assertEquals("1 film in my library", m.headline)
        assertEquals(1, m.lines.size)
        assertEquals(0f, m.progress, 0f)
    }

    @Test fun longListsAreCapped() {
        val entries = (1..20).map { e("F$it", null, it % 2 == 0) }
        val m = ShareCardPlanner.model("X", entries, 10, 20, true)
        assertEquals(ShareCardPlanner.MAX_LINES, m.lines.size)
        assertEquals(8, m.more)
        assertTrue(m.lines.size + m.more == 20)
    }
}
