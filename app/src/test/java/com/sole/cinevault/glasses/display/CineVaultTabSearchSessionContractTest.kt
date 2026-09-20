package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * D1-18 contract: top-level tab identity and Search query are session-owned.
 * Full Destination/backStack migration intentionally remains for later slices.
 */
class CineVaultTabSearchSessionContractTest {

    @Test
    fun sameSessionSharesTabAndSearchQueryAcrossSurfaces() {
        val registry = CineVaultComposeAppStateRegistry()
        val sessionId = CineVaultRenderSessionId(118L)

        val tablet = registry.stateFor(sessionId)
        val glasses = registry.stateFor(sessionId)

        assertSame(tablet, glasses)

        tablet.onTabSelected(2)
        tablet.onSearchQueryChanged("Jurassic")

        assertEquals(2, glasses.snapshot.value.selectedTab)
        assertEquals("Jurassic", glasses.snapshot.value.searchQuery)
        assertEquals(CineVaultAppSurface.SEARCH, glasses.snapshot.value.surface)
    }

    @Test
    fun externalTabChangeIsVisibleToTabletState() {
        val registry = CineVaultComposeAppStateRegistry()
        val sessionId = CineVaultRenderSessionId(119L)

        val tablet = registry.stateFor(sessionId)
        val glasses = registry.stateFor(sessionId)

        glasses.onTabSelected(1)

        assertEquals(1, tablet.snapshot.value.selectedTab)
        assertEquals(CineVaultAppSurface.LIBRARY, tablet.snapshot.value.surface)
    }
}
