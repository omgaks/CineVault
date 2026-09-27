package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class MediaServerPolicyTest {
    @Test fun episodeTitlesUseSeriesAndSeasonEpisodeNumbers() {
        val item = MediaServerItem(
            id = "e1",
            name = "Pilot",
            type = "Episode",
            mediaType = "Video",
            container = "mp4",
            overview = null,
            communityRating = null,
            primaryImageTag = null,
            seriesName = "Show",
            indexNumber = 2,
            parentIndexNumber = 1,
        )
        assertEquals("Show - S01E02 - Pilot", mediaServerItemTitle(item))
    }

    @Test fun movieTitlesRemainUnchanged() {
        val item = MediaServerItem(
            id = "m1",
            name = "Movie",
            type = "Movie",
            mediaType = "Video",
            container = "mkv",
            overview = null,
            communityRating = 8.2f,
            primaryImageTag = "tag1",
            seriesName = null,
            indexNumber = null,
            parentIndexNumber = null,
        )
        assertEquals("Movie", mediaServerItemTitle(item))
    }

    @Test fun networkTypesRemainDistinct() {
        assertEquals(NetworkType.JELLYFIN, NetworkType.valueOf("JELLYFIN"))
        assertEquals(NetworkType.EMBY, NetworkType.valueOf("EMBY"))
    }

    @Test fun serverUrlNormalizationRemovesTrailingSlashOnly() {
        assertEquals(
            "https://media.local:8920",
            normalizeServerUrl(" https://media.local:8920/ ")
        )
    }

    @Test fun sessionKeepsServerIdentitySeparateFromCredentials() {
        val session = MediaServerSession(
            serverUrl = "http://192.168.1.2:8096",
            userId = "user-1",
            accessToken = "token",
            serverId = "server-1",
        )
        assertEquals("server-1", session.serverId)
        assertEquals("user-1", session.userId)
        assertFalse(session.serverUrl.contains(session.accessToken))
    }
}
