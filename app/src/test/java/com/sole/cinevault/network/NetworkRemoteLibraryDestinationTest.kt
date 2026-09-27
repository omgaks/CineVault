package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRemoteLibraryDestinationTest {
    @Test fun playbackBridgePreservesNetworkPathAndName() {
        val remote = NetworkVideo(
            path = "https://media.example.test/movies/sample.mp4",
            name = "Sample",
        )
        val local = remote.toCineVaultVideoFile()
        assertEquals(remote.path, local.path)
        assertEquals(remote.name, local.name)
    }

    @Test fun playbackBridgeDoesNotRewriteSmbUri() {
        val remote = NetworkVideo(
            path = "smb://nas/Movies/Sample.mkv",
            name = "Sample",
        )
        assertEquals(remote.path, remote.toCineVaultVideoFile().path)
    }
}
