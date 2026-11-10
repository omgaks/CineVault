package com.sole.cinevault.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingBatteryTipPolicyTest {
    @Test
    fun showsWhileSharingIfNotExemptAndNotDismissed() {
        assertTrue(shouldShowSharingBatteryTip(sharingRunning = true, ignoringBatteryOptimizations = false, dismissed = false))
    }

    @Test
    fun hiddenWhenNotSharing() {
        assertFalse(shouldShowSharingBatteryTip(sharingRunning = false, ignoringBatteryOptimizations = false, dismissed = false))
    }

    @Test
    fun hiddenOnceAndroidAlreadyExemptsTheApp() {
        assertFalse(shouldShowSharingBatteryTip(sharingRunning = true, ignoringBatteryOptimizations = true, dismissed = false))
    }

    @Test
    fun hiddenForeverAfterDismiss() {
        assertFalse(shouldShowSharingBatteryTip(sharingRunning = true, ignoringBatteryOptimizations = false, dismissed = true))
    }
}
