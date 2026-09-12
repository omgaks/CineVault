package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoStreamProfileTest {

    @Test
    fun pqTransferIsClassifiedAsHdr() {
        assertEquals(
            VideoDynamicRange.HDR10_OR_PQ,
            classifyVideoDynamicRange(C.COLOR_TRANSFER_ST2084),
        )
    }

    @Test
    fun hlgTransferIsClassifiedAsHlg() {
        assertEquals(
            VideoDynamicRange.HLG,
            classifyVideoDynamicRange(C.COLOR_TRANSFER_HLG),
        )
    }

    @Test
    fun sdrTransferIsClassifiedAsSdr() {
        assertEquals(
            VideoDynamicRange.SDR,
            classifyVideoDynamicRange(C.COLOR_TRANSFER_SDR),
        )
    }

    @Test
    fun missingTransferIsUnknown() {
        assertEquals(
            VideoDynamicRange.UNKNOWN,
            classifyVideoDynamicRange(null),
        )
    }

    @Test
    fun profileLabels4kResolution() {
        val profile = VideoStreamProfile(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
            width = 3840,
            height = 2160,
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
        )

        assertEquals("4K", profile.resolutionLabel)
        assertEquals(8_294_400L, profile.pixelCount)
    }

    @Test
    fun unknownDimensionsHaveNoPixelCount() {
        val profile = VideoStreamProfile(
            mimeType = "video/avc",
            codecString = null,
            width = null,
            height = null,
            frameRate = null,
            dynamicRange = VideoDynamicRange.UNKNOWN,
        )

        assertEquals("Unknown", profile.resolutionLabel)
        assertNull(profile.pixelCount)
    }
}
