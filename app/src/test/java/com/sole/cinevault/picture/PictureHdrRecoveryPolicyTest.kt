package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureHdrRecoveryPolicyTest {
    @Test fun positionNeverNegative() = assertEquals(0L, PictureHdrRecoveryPolicy.snapshot(-20L, true).positionMs)
    @Test fun playStatePreserved() = assertFalse(PictureHdrRecoveryPolicy.snapshot(42L, false).playWhenReady)
    @Test fun firstRecoveryPermitted() = assertTrue(PictureHdrRecoveryPolicy.mayRetry(false, 0))
    @Test fun secondRecoveryBlocked() = assertFalse(PictureHdrRecoveryPolicy.mayRetry(false, 1))
    @Test fun alreadyHandledBlocked() = assertFalse(PictureHdrRecoveryPolicy.mayRetry(true, 0))
}
