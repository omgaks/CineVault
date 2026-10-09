package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureHdrPlaybackGateTest {
    private val gate = PictureHdrPlaybackGate
    private val pq = PictureHdrPolicy.Transfer.PQ
    private val hlg = PictureHdrPolicy.Transfer.HLG
    private val standard = PictureHdrPolicy.Format.STANDARD
    private val verified = PictureHdrGpuContract.Stage.DEVICE_VERIFIED

    @Test fun sdrKeepsOriginalEffect() {
        assertEquals(PictureHdrPlaybackGate.Action.SDR_EFFECT,
            gate.plan(PictureHdrPolicy.Transfer.SDR, standard).action)
    }

    @Test fun pqDefaultsToPassthrough() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(pq, standard).action)
    }

    @Test fun hlgDefaultsToPassthrough() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(hlg, standard).action)
    }

    @Test fun unknownAlwaysBypasses() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(PictureHdrPolicy.Transfer.UNKNOWN, standard, verified, true, true).action)
    }

    @Test fun dolbyVisionAlwaysBypasses() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(pq, PictureHdrPolicy.Format.DOLBY_VISION, verified, true, true).action)
    }

    @Test fun verifiedButNoDeviceSupportBypasses() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(pq, standard, verified, false, true).action)
    }

    @Test fun verifiedButNoRecoveryBypasses() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(pq, standard, verified, true, false).action)
    }

    @Test fun verifiedWithBothSafeguardsAllowsIdentity() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_IDENTITY_EFFECT,
            gate.plan(hlg, standard, verified, true, true).action)
    }

    @Test fun isolatedStageNeverEnablesIdentity() {
        assertEquals(PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH,
            gate.plan(pq, standard,
                PictureHdrGpuContract.Stage.ISOLATED_GPU_IDENTITY, true, true).action)
    }
}
