package com.sole.cinevault

import com.sole.cinevault.collections.CollectionPlan
import com.sole.cinevault.collections.CollectionPart
import com.sole.cinevault.collections.CollectionSlot
import com.sole.cinevault.collections.FranchiseStripLogic
import com.sole.cinevault.collections.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseStripLogicTest {
    private fun slot(id: Int, status: SlotStatus) =
        CollectionSlot(CollectionPart(id, "Film $id", "2004-01-01", null, null), status, id, null, null, null)

    private fun plan(slots: List<CollectionSlot>, owned: Int, released: Int, full: Boolean = true) =
        CollectionPlan(slots, owned, released, 0, emptyList(), null, full)

    @Test fun ownedOneOfTwoStillShowsTheStrip() {
        val p = plan(listOf(slot(1, SlotStatus.OWNED), slot(2, SlotStatus.MISSING)), 1, 2)
        assertTrue(FranchiseStripLogic.shouldShow(p))
        assertEquals("The Incredibles Collection · 1 of 2 in your library", FranchiseStripLogic.header("The Incredibles Collection", p))
    }

    @Test fun standaloneFilmHidesTheStrip() {
        assertFalse(FranchiseStripLogic.shouldShow(plan(listOf(slot(1, SlotStatus.OWNED)), 1, 1)))
        assertFalse(FranchiseStripLogic.shouldShow(plan(emptyList(), 0, 0, full = false)))
    }

    @Test fun currentIndexFindsTheFilm() {
        val p = plan(listOf(slot(10, SlotStatus.MISSING), slot(20, SlotStatus.OWNED)), 1, 2)
        assertEquals(1, FranchiseStripLogic.currentIndex(p, 20))
        assertEquals(-1, FranchiseStripLogic.currentIndex(p, 99))
        assertEquals(-1, FranchiseStripLogic.currentIndex(p, null))
    }

    @Test fun offlineHeaderHasNoTotal() {
        val p = plan(listOf(slot(1, SlotStatus.OWNED), slot(2, SlotStatus.OWNED)), 2, 2, full = false)
        assertEquals("Saga · 2 in your library", FranchiseStripLogic.header("Saga", p))
    }
}
