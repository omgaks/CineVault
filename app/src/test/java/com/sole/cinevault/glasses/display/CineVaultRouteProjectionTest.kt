package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D1-19 protects the route projection contract used by CineVaultApp:
 * real CineVault model objects remain outside the display package; only their
 * stable identities cross into the shared session.
 */
class CineVaultRouteProjectionTest {

    private val keys = CineVaultNavigationKeyFactory()

    @Test
    fun detailPlayerAndTvRoutesHaveSeparateStableNamespaces() {
        assertEquals(
            "detail:/movies/Jurassic Park.mkv",
            keys.detail("/movies/Jurassic Park.mkv"),
        )
        assertEquals(
            "video:/movies/Jurassic Park.mkv",
            keys.video("/movies/Jurassic Park.mkv"),
        )
        assertEquals(
            "tv:Jurassic Park",
            keys.tvGroup("Jurassic Park"),
        )
    }

    @Test
    fun rawIdentityCanBeRecoveredWithoutOwningCineVaultModels() {
        val key = keys.video("/storage/emulated/0/Movies/Alien.mkv")

        assertEquals(
            "/storage/emulated/0/Movies/Alien.mkv",
            keys.rawIdentity(key),
        )
    }
}
