package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCodecDetailsTest {

    @Test
    fun parsesHevcMain10ProfileAndLevel() {
        val details = parseVideoCodecDetails(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
        )

        assertEquals("HEVC", details.codecLabel)
        assertEquals("Main 10", details.profileLabel)
        assertEquals(10, details.inferredBitDepth)
        assertEquals("L5.1", details.levelLabel)
    }

    @Test
    fun parsesAvcHighProfileAndLevel() {
        val details = parseVideoCodecDetails(
            mimeType = "video/avc",
            codecString = "avc1.640028",
        )

        assertEquals("H.264", details.codecLabel)
        assertEquals("High", details.profileLabel)
        assertNull(details.inferredBitDepth)
        assertEquals("L4.0", details.levelLabel)
    }

    @Test
    fun parsesAv1BitDepthWhenCodecStringCarriesIt() {
        val details = parseVideoCodecDetails(
            mimeType = "video/av01",
            codecString = "av01.0.08M.10",
        )

        assertEquals("AV1", details.codecLabel)
        assertEquals("Main", details.profileLabel)
        assertEquals(10, details.inferredBitDepth)
        assertEquals("L8", details.levelLabel)
    }

    @Test
    fun parsesVp9ProfileLevelAndBitDepth() {
        val details = parseVideoCodecDetails(
            mimeType = "video/x-vnd.on2.vp9",
            codecString = "vp09.02.41.10",
        )

        assertEquals("VP9", details.codecLabel)
        assertEquals("Profile 2", details.profileLabel)
        assertEquals(10, details.inferredBitDepth)
        assertEquals("L4.1", details.levelLabel)
    }

    @Test
    fun missingCodecStringDoesNotInventProfileOrBitDepth() {
        val details = parseVideoCodecDetails(
            mimeType = "video/hevc",
            codecString = null,
        )

        assertEquals("HEVC", details.codecLabel)
        assertNull(details.profileLabel)
        assertNull(details.levelLabel)
        assertNull(details.inferredBitDepth)
    }

    @Test
    fun formatsOnlyDetailsWeActuallyKnow() {
        val text = formatVideoCodecDetails(
            VideoCodecDetails(
                codecLabel = "HEVC",
                profileLabel = "Main 10",
                levelLabel = "L5.1",
                inferredBitDepth = 10,
            )
        )

        assertEquals(
            "HEVC · Main 10 · 10-bit · L5.1",
            text,
        )
    }
}
