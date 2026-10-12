package com.sole.cinevault

import com.sole.cinevault.metadata.castNeedsRefresh
import com.sole.cinevault.metadata.validCast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastValidityTest {
    @Test fun emptyOrBrokenCastIsRefreshed() {
        assertTrue(castNeedsRefresh(null))
        assertTrue(castNeedsRefresh(emptyList()))
        assertTrue(castNeedsRefresh(listOf(CastEntry(0, "x", null))))
    }

    @Test fun goodCastIsKept() {
        val good = listOf(CastEntry(73457, "Chris Pratt", "/p.jpg"))
        assertFalse(castNeedsRefresh(good))
        assertEquals(good, validCast(good))
        assertEquals(emptyList<CastEntry>(), validCast(listOf(CastEntry(0, "x", null))))
    }
}
