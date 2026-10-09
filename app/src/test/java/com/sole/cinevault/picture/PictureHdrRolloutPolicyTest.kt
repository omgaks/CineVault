package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureHdrRolloutPolicyTest {
    private val isolated = PictureHdrGpuContract.Stage.ISOLATED_GPU_IDENTITY
    private val verified = PictureHdrGpuContract.Stage.DEVICE_VERIFIED
    private val pq = PictureHdrPolicy.Transfer.PQ
    private val standard = PictureHdrPolicy.Format.STANDARD
    @Test fun productionIsOff() = assertFalse(PictureHdrRolloutPolicy.canActivateHdrGpu(isolated, true, true, pq, standard))
    @Test fun deviceMustSupportHdr() = assertFalse(PictureHdrRolloutPolicy.canActivateHdrGpu(verified, false, true, pq, standard))
    @Test fun recoveryMustBeVerified() = assertFalse(PictureHdrRolloutPolicy.canActivateHdrGpu(verified, true, false, pq, standard))
    @Test fun verifiedPqMayBeEnabled() = assertTrue(PictureHdrRolloutPolicy.canActivateHdrGpu(verified, true, true, pq, standard))
    @Test fun dolbyVisionNeverEnabled() = assertFalse(PictureHdrRolloutPolicy.canActivateHdrGpu(verified, true, true, pq, PictureHdrPolicy.Format.DOLBY_VISION))
}
