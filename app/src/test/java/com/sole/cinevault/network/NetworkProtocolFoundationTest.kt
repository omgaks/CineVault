package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class NetworkProtocolFoundationTest {
    @Test fun sourceIdValidationRejectsCredentialMaterial() {
        assertTrue(isSafeNetworkSourceId("webdav-0123456789abcdefabcd"))
        assertFalse(isSafeNetworkSourceId("webdav://user:pass@server"))
        assertFalse(isSafeNetworkSourceId("sftp-user-secret"))
    }

    @Test fun m3uParsesNamedHttpEntriesAndIgnoresComments() {
        val entries = parseM3u("""
            #EXTM3U
            #EXTINF:-1,Movie One
            https://server.local/movie1.mkv
            # ignored
            https://server.local/movie2.mp4
        """.trimIndent())
        assertEquals(2, entries.size)
        assertEquals("Movie One", entries[0].name)
        assertEquals("movie2.mp4", entries[1].name)
    }

    @Test fun m3uRejectsCredentialBearingUrls() {
        assertTrue(parseM3u("https://user:secret@server.local/movie.mkv").isEmpty())
    }

    @Test fun httpDirectoryKeepsOnlyVideoLinks() {
        val videos = extractHttpDirectoryVideos(
            """<a href="film.mkv">Film</a><a href="poster.jpg">Poster</a>""",
            "https://server.local/media/index.html",
        )
        assertEquals(1, videos.size)
        assertEquals("https://server.local/media/film.mkv", videos.single().path)
    }

    @Test fun webDavParserExtractsVideoHrefs() {
        val videos = extractWebDavVideoHrefs(
            """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/Film%20One.mkv</d:href></d:response></d:multistatus>""",
            "https://server.local/dav/",
        )
        assertEquals(1, videos.size)
        assertEquals("https://server.local/dav/Film%20One.mkv", videos.single().path)
    }
}
