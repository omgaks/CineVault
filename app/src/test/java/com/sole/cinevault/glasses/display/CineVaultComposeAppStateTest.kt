package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultComposeAppStateTest {

    @Test
    fun sameSessionReturnsSameObservableBridge() {
        val registry = CineVaultComposeAppStateRegistry()
        val id = CineVaultRenderSessionId(81L)

        assertSame(registry.stateFor(id), registry.stateFor(id))
    }

    @Test
    fun mutationsPublishLatestSharedSnapshot() {
        val shared = CineVaultSharedAppState(CineVaultRenderSessionId(82L))
        val compose = CineVaultComposeAppState(shared.sessionId, shared)

        compose.onTabSelected(2)
        compose.onSearchQueryChanged("Alien")
        compose.onDetailSelected("detail:/movies/Alien.mkv")

        assertEquals(CineVaultAppSurface.DETAIL, compose.snapshot.value.surface)
        assertEquals("Alien", compose.snapshot.value.searchQuery)
        assertEquals(
            "detail:/movies/Alien.mkv",
            compose.snapshot.value.selectedDetailKey,
        )
    }

    @Test
    fun publishCurrentSnapshotBridgesIncrementalLegacyMutation() {
        val shared = CineVaultSharedAppState(CineVaultRenderSessionId(83L))
        val compose = CineVaultComposeAppState(shared.sessionId, shared)

        shared.selectTab(1)
        assertEquals(CineVaultAppSurface.HOME, compose.snapshot.value.surface)

        compose.publishCurrentSnapshot()

        assertEquals(CineVaultAppSurface.LIBRARY, compose.snapshot.value.surface)
    }

    @Test
    fun backPublishesOnlyWhenHandled() {
        val shared = CineVaultSharedAppState(CineVaultRenderSessionId(84L))
        val compose = CineVaultComposeAppState(shared.sessionId, shared)

        assertFalse(compose.onBack())

        compose.onTabSelected(2)
        assertTrue(compose.onBack())
        assertEquals(CineVaultAppSurface.HOME, compose.snapshot.value.surface)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedSessionIsRejected() {
        CineVaultComposeAppState(
            sessionId = CineVaultRenderSessionId(85L),
            sharedState = CineVaultSharedAppState(CineVaultRenderSessionId(86L)),
        )
    }

    @Test
    fun releaseCreatesFreshComposeBridge() {
        val registry = CineVaultComposeAppStateRegistry()
        val id = CineVaultRenderSessionId(87L)

        val first = registry.stateFor(id)
        first.onTabSelected(2)

        registry.release(id)

        val second = registry.stateFor(id)
        assertEquals(CineVaultAppSurface.HOME, second.snapshot.value.surface)
    }
}
