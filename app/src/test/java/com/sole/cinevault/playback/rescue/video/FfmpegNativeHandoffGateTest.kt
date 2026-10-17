package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegNativeHandoffGateTest {
    @Test fun fullyReadyHandoffMayActivate() {
        assertTrue(FfmpegNativeHandoffGate.canActivate(true, true, true, true, false))
    }

    @Test fun missingAnyOwnershipRequirementRejectsActivation() {
        assertFalse(FfmpegNativeHandoffGate.canActivate(false, true, true, true, false))
        assertFalse(FfmpegNativeHandoffGate.canActivate(true, false, true, true, false))
        assertFalse(FfmpegNativeHandoffGate.canActivate(true, true, false, true, false))
        assertFalse(FfmpegNativeHandoffGate.canActivate(true, true, true, false, false))
        assertFalse(FfmpegNativeHandoffGate.canActivate(true, true, true, true, true))
    }
}
