package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class RemoteLibraryPolicyTest {
    @Test fun friendlyTypesCoverEveryNetworkEngine() {
        NetworkType.entries.forEach { assertTrue(it.friendlyType().isNotBlank()) }
    }

    @Test fun playerBridgePreservesRemotePathWithoutCredentials() {
        val source = object : NetworkSource {
            override val id = "net_test"
            override val displayName = "Home Library"
            override val type = NetworkType.HTTP_DIRECTORY
            override fun createDataSourceFactory() = throw UnsupportedOperationException()
            override suspend fun scan() = emptyList<NetworkVideo>()
            override suspend fun testConnection() = NetworkConnectionResult.Connected
        }
        val video = NetworkVideo(
            path = "https://media.local/movies/Arrival.mp4",
            name = "Arrival.mp4",
            rating = 8.0f,
        )
        val bridged = video.toPlayerMetadata(source)
        assertEquals(video.path, bridged.video.path)
        assertEquals("Arrival", bridged.title)
        assertEquals("Home Library", bridged.subtitle)
        assertFalse(containsCredentialMaterial(bridged.video.path))
    }

    @Test fun duplicateRemoteItemsCanBeCollapsedByPath() {
        val videos = listOf(
            NetworkVideo("/a/movie.mkv", "Movie"),
            NetworkVideo("/a/movie.mkv", "Movie duplicate"),
            NetworkVideo("/b/movie.mkv", "Other"),
        )
        assertEquals(2, videos.distinctBy { it.path }.size)
    }
}
