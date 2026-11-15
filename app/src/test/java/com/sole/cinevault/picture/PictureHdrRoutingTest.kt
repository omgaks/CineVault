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

    @Test fun `BT709 AVC without transfer can use SDR enhancement`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT709).build()).build()
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(input).route)
    }

    @Test fun `BT709 HEVC without transfer is recognized as SDR`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT709).build()).build()
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(input).route)
    }

    @Test fun `BT601 HEVC without transfer is recognized as SDR`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT601).build()).build()
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(input).route)
    }

    @Test fun `BT709 HEVC with PQ remains HDR protected`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(ColorInfo.Builder()
                .setColorSpace(C.COLOR_SPACE_BT709)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084).build()).build()
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(input).route)
    }

    @Test fun `metadata light AVC enables enhancement`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(input).route)
    }
    @Test fun `metadata light HEVC remains protected`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(input).route)
    }

    @Test fun `Dolby Vision codec identifier protects HEVC with absent colour metadata`() {
        for (codec in listOf("dvh1.05.06", "dvhe.08.04", "dvc1.05.06", "dvav.01.01")) {
            val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs(codec).build()
            assertEquals(codec, PictureHdrPolicy.Route.HDR_PASSTHROUGH,
                PictureHdrRouting.decide(input).route)
        }
    }

    @Test fun `linear transfer enables SDR enhancement`() {
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT,
            PictureHdrRouting.decide(format(C.COLOR_TRANSFER_LINEAR)).route)
    }

    @Test fun `BT2020 overrides nominal SDR transfer`() {
        val input = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(ColorInfo.Builder()
                .setColorTransfer(C.COLOR_TRANSFER_SDR)
                .setColorSpace(C.COLOR_SPACE_BT2020).build()).build()
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH,
            PictureHdrRouting.decide(input).route)
    }

}
