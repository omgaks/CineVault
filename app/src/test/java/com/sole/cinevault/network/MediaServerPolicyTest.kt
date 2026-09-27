package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class MediaServerPolicyTest {
    @Test fun authenticationResponseRequiresTokenAndUserId() {
        val session = parseMediaServerAuthentication(
            """{"AccessToken":"abc123","ServerId":"server","User":{"Id":"user-1"}}""",
            "http://192.168.1.2:8096/",
        )
        assertNotNull(session)
        assertEquals("user-1", session!!.userId)
        assertEquals("abc123", session.accessToken)
        assertEquals("http://192.168.1.2:8096", session.serverUrl)

        assertNull(parseMediaServerAuthentication("""{"User":{"Id":"user-1"}}""", "http://host"))
        assertNull(parseMediaServerAuthentication("""{"AccessToken":"abc"}""", "http://host"))
    }

    @Test fun itemParserHandlesMoviesAndEpisodes() {
        val items = parseMediaServerItems(
            """{"Items":[
              {"Id":"m1","Name":"Movie","Type":"Movie","MediaType":"Video","Container":"mkv","CommunityRating":8.2,"PrimaryImageTag":"tag1"},
              {"Id":"e1","Name":"Pilot","Type":"Episode","MediaType":"Video","Container":"mp4","SeriesName":"Show","ParentIndexNumber":1,"IndexNumber":2}
            ]}"""
        )
        assertEquals(2, items.size)
        assertEquals("Movie", mediaServerItemTitle(items[0]))
        assertEquals("Show - S01E02 - Pilot", mediaServerItemTitle(items[1]))
        assertEquals(8.2f, items[0].communityRating!!, 0.001f)
    }

    @Test fun malformedItemsAreIgnoredWithoutCrashing() {
        assertTrue(parseMediaServerItems("not json").isEmpty())
        val items = parseMediaServerItems("""{"Items":[{"Name":"No Id"},{"Id":"x"}]}""")
        assertTrue(items.isEmpty())
    }

    @Test fun networkTypesRemainDistinct() {
        assertEquals(NetworkType.JELLYFIN, NetworkType.valueOf("JELLYFIN"))
        assertEquals(NetworkType.EMBY, NetworkType.valueOf("EMBY"))
    }

    @Test fun serverUrlNormalizationRemovesTrailingSlashOnly() {
        assertEquals("https://media.local:8920", normalizeServerUrl(" https://media.local:8920/ "))
    }
}
