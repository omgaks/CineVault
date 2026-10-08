package com.sole.cinevault.picture

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureHdrRoutingTest {
    private fun format(transfer: Int, mime: String = MimeTypes.VIDEO_H264): Format =
        Format.Builder().setSampleMimeType(mime)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).build()

    @Test fun `SDR video retains enhancement`() {
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR)).route)
    }

    @Test fun `PQ video bypasses SDR shader`() {
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(format(C.COLOR_TRANSFER_ST2084)).route)
    }

    @Test fun `HLG video bypasses SDR shader`() {
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(format(C.COLOR_TRANSFER_HLG)).route)
    }

    @Test fun `Dolby Vision bypasses even with SDR metadata`() {
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR, MimeTypes.VIDEO_DOLBY_VISION)).route)
    }

    @Test fun `missing transfer metadata retains legacy SDR behavior`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(input).route)
    }
}
