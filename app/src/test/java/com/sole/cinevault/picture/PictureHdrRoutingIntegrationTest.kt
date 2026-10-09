package com.sole.cinevault.picture

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P8-S7 regression checks for the routing path consumed by PictureController. */
class PictureHdrRoutingIntegrationTest {
    private fun format(transfer: Int, mime: String = MimeTypes.VIDEO_H264): Format =
        Format.Builder().setSampleMimeType(mime)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).build()

    @Test fun sdrRemainsEnhanceable() = assertEquals(
        PictureHdrPolicy.Route.SDR_ENHANCEMENT,
        PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR)).route,
    )

    @Test fun pqIsProtected() = assertEquals(
        PictureHdrPolicy.Route.HDR_PASSTHROUGH,
        PictureHdrRouting.decide(format(C.COLOR_TRANSFER_ST2084)).route,
    )

    @Test fun hlgIsProtected() = assertEquals(
        PictureHdrPolicy.Route.HDR_PASSTHROUGH,
        PictureHdrRouting.decide(format(C.COLOR_TRANSFER_HLG)).route,
    )

    @Test fun dolbyVisionIsProtectedEvenWithSdrMetadata() = assertEquals(
        PictureHdrPolicy.Route.HDR_PASSTHROUGH,
        PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR, MimeTypes.VIDEO_DOLBY_VISION)).route,
    )

    @Test fun unknownTransferIsProtected() = assertEquals(
        PictureHdrPolicy.Route.HDR_PASSTHROUGH,
        PictureHdrRouting.decide(format(Format.NO_VALUE)).route,
    )

    @Test fun missingMetadataPreservesLegacyBehavior() = assertEquals(
        PictureHdrPolicy.Route.SDR_ENHANCEMENT,
        PictureHdrRouting.decide(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()).route,
    )

    @Test fun pqToSdrTrackTransitionReevaluates() {
        val pq = PictureHdrRouting.decide(format(C.COLOR_TRANSFER_ST2084))
        val sdr = PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR))
        assertTrue(pq.hdrDetected)
        assertFalse(sdr.hdrDetected)
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT, sdr.route)
    }

    @Test fun sdrToHlgTrackTransitionReevaluates() {
        val sdr = PictureHdrRouting.decide(format(C.COLOR_TRANSFER_SDR))
        val hlg = PictureHdrRouting.decide(format(C.COLOR_TRANSFER_HLG))
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT, sdr.route)
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, hlg.route)
    }
}
