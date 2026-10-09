package com.sole.cinevault.picture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureHdrGpuContractTest {
    private val pq = PictureHdrPolicy.Transfer.PQ
    private val hlg = PictureHdrPolicy.Transfer.HLG
    private val standard = PictureHdrPolicy.Format.STANDARD
    private val verified = PictureHdrGpuContract.Stage.DEVICE_VERIFIED

    @Test fun pqIsNotInstalledBeforeDeviceValidation() {
        assertFalse(PictureHdrGpuContract.canInstallForPlayback(pq, standard))
    }

    @Test fun hlgIsNotInstalledBeforeDeviceValidation() {
        assertFalse(PictureHdrGpuContract.canInstallForPlayback(hlg, standard))
    }

    @Test fun verifiedPqCanBeEligibleForFutureIntegration() {
        assertTrue(PictureHdrGpuContract.canInstallForPlayback(pq, standard, verified))
    }

    @Test fun verifiedHlgCanBeEligibleForFutureIntegration() {
        assertTrue(PictureHdrGpuContract.canInstallForPlayback(hlg, standard, verified))
    }

    @Test fun dolbyVisionNeverQualifies() {
        assertFalse(PictureHdrGpuContract.canInstallForPlayback(
            pq, PictureHdrPolicy.Format.DOLBY_VISION, verified))
    }

    @Test fun sdrAndUnknownNeverQualify() {
        assertFalse(PictureHdrGpuContract.canInstallForPlayback(
            PictureHdrPolicy.Transfer.SDR, standard, verified))
        assertFalse(PictureHdrGpuContract.canInstallForPlayback(
            PictureHdrPolicy.Transfer.UNKNOWN, standard, verified))
    }
}
