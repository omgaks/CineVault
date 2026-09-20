package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultNavigationAdapterTest {

    @Test
    fun twoAdaptersForSameSession_shareNavigationState() {
        val registry = CineVaultNavigationAdapterRegistry()
        val sessionId = CineVaultRenderSessionId(71L)

        val tablet = registry.adapterFor(sessionId)
        val glasses = registry.adapterFor(sessionId)

        tablet.onTabSelected(2)
        tablet.onSearchQueryChanged("Jurassic")
        glasses.onDetailSelected("/movies/Jurassic Park.mkv")

        assertEquals(CineVaultAppSurface.DETAIL, tablet.snapshot.surface)
        assertEquals("Jurassic", glasses.snapshot.searchQuery)
        assertEquals(
            "detail:/movies/Jurassic Park.mkv",
            tablet.snapshot.selectedDetailKey,
        )
    }

    @Test
    fun playerActionsUseOneVideoKeyContract() {
        val adapter = CineVaultNavigationAdapter(
            CineVaultSharedAppState(CineVaultRenderSessionId(72L)),
        )

        adapter.onPlayFromDetail("/movies/a.mkv")
        assertEquals("video:/movies/a.mkv", adapter.snapshot.selectedVideoKey)

        adapter.onEpisodeSelected("/shows/s01e02.mkv")
        assertEquals("video:/shows/s01e02.mkv", adapter.snapshot.selectedVideoKey)

        adapter.onPlayNext("/shows/s01e03.mkv")
        assertEquals("video:/shows/s01e03.mkv", adapter.snapshot.selectedVideoKey)
    }

    @Test
    fun tabSelectionClearsTransientRoutesThroughExistingStateContract() {
        val adapter = CineVaultNavigationAdapter(
            CineVaultSharedAppState(CineVaultRenderSessionId(73L)),
        )

        adapter.onDetailSelected("/movies/a.mkv")
        adapter.onVideoSelected("/movies/a.mkv")
        adapter.onTabSelected(1)

        assertEquals(CineVaultAppSurface.LIBRARY, adapter.snapshot.surface)
        assertEquals(null, adapter.snapshot.selectedVideoKey)
        assertEquals(null, adapter.snapshot.selectedDetailKey)
        assertEquals(null, adapter.snapshot.selectedTvGroupKey)
    }

    @Test
    fun backDelegatesToSharedNavigationOwner() {
        val adapter = CineVaultNavigationAdapter(
            CineVaultSharedAppState(CineVaultRenderSessionId(74L)),
        )

        adapter.onTabSelected(2)
        adapter.onDetailSelected("/movies/a.mkv")
        adapter.onVideoSelected("/movies/a.mkv")

        assertTrue(adapter.onBack())
        assertEquals(CineVaultAppSurface.DETAIL, adapter.snapshot.surface)

        assertTrue(adapter.onBack())
        assertEquals(CineVaultAppSurface.SEARCH, adapter.snapshot.surface)

        assertTrue(adapter.onBack())
        assertEquals(CineVaultAppSurface.HOME, adapter.snapshot.surface)

        assertFalse(adapter.onBack())
    }

    @Test
    fun keyFactoryNamespacesAndRecoversIdentity() {
        val keys = CineVaultNavigationKeyFactory()

        val video = keys.video(" /storage/emulated/0/Movies/A.mkv ")
        val detail = keys.detail("/storage/emulated/0/Movies/A.mkv")
        val tv = keys.tvGroup("Jurassic Collection")

        assertEquals("video:/storage/emulated/0/Movies/A.mkv", video)
        assertEquals("detail:/storage/emulated/0/Movies/A.mkv", detail)
        assertEquals("tv:Jurassic Collection", tv)
        assertEquals(
            "/storage/emulated/0/Movies/A.mkv",
            keys.rawIdentity(video),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNavigationIdentityIsRejected() {
        CineVaultNavigationKeyFactory().video("   ")
    }
}
