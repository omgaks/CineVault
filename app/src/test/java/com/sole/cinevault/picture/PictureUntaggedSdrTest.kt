package com.sole.cinevault.picture

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureUntaggedSdrTest {
    private fun hevc(w: Int, h: Int, codecs: String? = null, colour: ColorInfo? = null): Format =
        Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(w).setHeight(h)
            .apply { if (codecs != null) setCodecs(codecs) }
            .apply { if (colour != null) setColorInfo(colour) }.build()

    private val protectedRoute = PictureHdrPolicy.Route.HDR_PASSTHROUGH
    private val sdrRoute = PictureHdrPolicy.Route.SDR_ENHANCEMENT

    @Test fun untaggedHevc1080pIsProtectedByDefault() {
        assertEquals(protectedRoute, PictureHdrRouting.decide(hevc(1920, 1080)).route)
    }

    @Test fun untaggedHevc1080pIsSdrWhenAssumed() {
        val f = hevc(1920, 1080)
        assertEquals(sdrRoute, PictureHdrRouting.decide(f, assumeUntaggedSdr = true).route)
        assertTrue(PictureHdrRouting.isUntaggedAssumable(f))
    }

    @Test fun untagged4kStaysProtected() {
        val f = hevc(3840, 2160)
        assertEquals(protectedRoute, PictureHdrRouting.decide(f, assumeUntaggedSdr = true).route)
        assertFalse(PictureHdrRouting.isUntaggedAssumable(f))
    }

    @Test fun dolbyVisionNeverAssumedSdr() {
        val f = hevc(1920, 1080, codecs = "dvhe.05.06")
        assertEquals(protectedRoute, PictureHdrRouting.decide(f, assumeUntaggedSdr = true).route)
        assertFalse(PictureHdrRouting.isUntaggedAssumable(f))
    }

    @Test fun explicitHdrSignalsNeverAssumedSdr() {
        for (transfer in listOf(C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG)) {
            val f = hevc(1920, 1080, colour = ColorInfo.Builder().setColorTransfer(transfer).build())
            assertEquals(protectedRoute, PictureHdrRouting.decide(f, assumeUntaggedSdr = true).route)
        }
        val bt2020 = hevc(1920, 1080, colour = ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).build())
        assertEquals(protectedRoute, PictureHdrRouting.decide(bt2020, assumeUntaggedSdr = true).route)
    }

    @Test fun sizeUnknownIsNotAssumable() {
        assertFalse(PictureHdrRouting.isUntaggedAssumable(hevc(0, 0)))
    }
}
