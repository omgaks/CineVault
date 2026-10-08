package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureHdrPolicyTest {
    private val policy = PictureHdrPolicy

    @Test fun `SDR retains existing enhancement route`() {
        val decision = policy.decide(PictureHdrPolicy.Input(PictureHdrPolicy.Transfer.SDR))
        assertEquals(PictureHdrPolicy.Route.SDR_ENHANCEMENT, decision.route)
        assertFalse(decision.hdrDetected)
    }

    @Test fun `PQ bypasses SDR enhancement`() {
        val decision = policy.decide(PictureHdrPolicy.Input(PictureHdrPolicy.Transfer.PQ))
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, decision.route)
        assertTrue(decision.hdrDetected)
    }

    @Test fun `HLG bypasses SDR enhancement`() {
        val decision = policy.decide(PictureHdrPolicy.Input(PictureHdrPolicy.Transfer.HLG))
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, decision.route)
    }

    @Test fun `Dolby Vision bypasses even with SDR transfer metadata`() {
        val decision = policy.decide(PictureHdrPolicy.Input(
            PictureHdrPolicy.Transfer.SDR, PictureHdrPolicy.Format.DOLBY_VISION))
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, decision.route)
        assertTrue(decision.hdrDetected)
    }

    @Test fun `unknown transfer fails safely`() {
        val decision = policy.decide(PictureHdrPolicy.Input(PictureHdrPolicy.Transfer.UNKNOWN))
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, decision.route)
    }

    @Test fun `feature flag cannot enable unimplemented HDR shader`() {
        val decision = policy.decide(PictureHdrPolicy.Input(
            PictureHdrPolicy.Transfer.PQ, hdrEffectSupported = true))
        assertEquals(PictureHdrPolicy.Route.HDR_PASSTHROUGH, decision.route)
    }
}
