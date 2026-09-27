package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class DlnaPolicyTest {
    @Test fun ssdpSearchTargetsMediaServers() {
        val request = buildSsdpSearch()
        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1"))
        assertTrue(request.contains("239.255.255.250:1900"))
        assertTrue(request.contains("urn:schemas-upnp-org:device:MediaServer:1"))
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test fun ssdpParserRequiresSafeLocationAndUsn() {
        val response = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.20:8200/rootDesc.xml
            USN: uuid:abc::urn:schemas-upnp-org:device:MediaServer:1
            SERVER: Test/1.0 UPnP/1.1
        """.trimIndent().replace("\n", "\r\n")

        val device = parseSsdpResponse(response)
        assertNotNull(device)
        assertEquals("http://192.168.1.20:8200/rootDesc.xml", device!!.location)
        assertTrue(device.usn.startsWith("uuid:"))
        assertNull(parseSsdpResponse("HTTP/1.1 200 OK\r\nUSN: uuid:x\r\n\r\n"))
    }

    @Test fun urlResolutionSupportsAbsoluteAndRelativeControlUrls() {
        assertEquals(
            "http://192.168.1.20:8200/ctl/ContentDir",
            resolveDlnaUrl(
                "http://192.168.1.20:8200/rootDesc.xml",
                "/ctl/ContentDir",
            )
        )
        assertEquals(
            "http://192.168.1.20:8200/control",
            resolveDlnaUrl(
                "http://192.168.1.20:8200/device/root.xml",
                "../../control",
            )
        )
    }

    @Test fun mimePolicyAllowsPlayableMediaOnly() {
        assertTrue(isDlnaMediaMime("video/mp4"))
        assertTrue(isDlnaMediaMime("audio/flac"))
        assertFalse(isDlnaMediaMime("image/jpeg"))
        assertFalse(isDlnaMediaMime(null))
    }

    @Test fun browseEnvelopeEscapesObjectId() {
        val envelope = browseEnvelope("folder&<1>")
        assertTrue(envelope.contains("folder&amp;&lt;1&gt;"))
        assertTrue(envelope.contains("BrowseDirectChildren"))
    }
}
