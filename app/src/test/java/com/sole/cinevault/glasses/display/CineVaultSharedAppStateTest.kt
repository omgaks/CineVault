package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultSharedAppStateTest {

    @Test
    fun hostAndExternal_sameSession_receiveSameStateOwner() {
        val registry = CineVaultSharedAppStateRegistry()
        val sessionId = CineVaultRenderSessionId(61L)

        val host = registry.stateFor(sessionId)
        val external = registry.stateFor(sessionId)

        assertSame(host, external)
    }

    @Test
    fun stateChangedByOneSurface_isImmediatelySessionState() {
        val registry = CineVaultSharedAppStateRegistry()
        val sessionId = CineVaultRenderSessionId(62L)

        val tablet = registry.stateFor(sessionId)
        val glasses = registry.stateFor(sessionId)

        tablet.selectTab(2)
        tablet.setSearchQuery("Jurassic")
        glasses.openDetail("movie:/jurassic-park")

        assertEquals(CineVaultAppSurface.DETAIL, tablet.snapshot.surface)
        assertEquals("Jurassic", glasses.snapshot.searchQuery)
        assertEquals("movie:/jurassic-park", tablet.snapshot.selectedDetailKey)
    }

    @Test
    fun playerHasHighestSurfacePriority() {
        val state = CineVaultSharedAppState(CineVaultRenderSessionId(63L))

        state.openDetail("detail:1")
        state.playVideo("video:1")

        assertEquals(CineVaultAppSurface.PLAYER, state.snapshot.surface)
    }

    @Test
    fun back_followsCurrentCineVaultNavigationPriority() {
        val state = CineVaultSharedAppState(CineVaultRenderSessionId(64L))

        state.selectTab(2)
        state.openTvGroup("tv:1")
        state.openDetail("detail:1")
        state.playVideo("video:1")

        assertTrue(state.back())
        assertEquals(CineVaultAppSurface.DETAIL, state.snapshot.surface)

        assertTrue(state.back())
        assertEquals(CineVaultAppSurface.SEARCH, state.snapshot.surface)

        assertTrue(state.back())
        assertEquals(CineVaultAppSurface.HOME, state.snapshot.surface)

        assertFalse(state.back())
    }

    @Test
    fun selectingTab_clearsTransientNavigationSelections() {
        val state = CineVaultSharedAppState(CineVaultRenderSessionId(65L))

        state.openDetail("detail:1")
        state.playVideo("video:1")
        state.selectTab(1)

        assertEquals(CineVaultAppSurface.LIBRARY, state.snapshot.surface)
        assertEquals(null, state.snapshot.selectedVideoKey)
        assertEquals(null, state.snapshot.selectedDetailKey)
        assertEquals(null, state.snapshot.selectedTvGroupKey)
    }

    @Test
    fun releasingSession_createsFreshOwnerNextTime() {
        val registry = CineVaultSharedAppStateRegistry()
        val sessionId = CineVaultRenderSessionId(66L)

        val first = registry.stateFor(sessionId)
        first.selectTab(2)

        registry.release(sessionId)
        val second = registry.stateFor(sessionId)

        assertEquals(CineVaultAppSurface.HOME, second.snapshot.surface)
    }
}
