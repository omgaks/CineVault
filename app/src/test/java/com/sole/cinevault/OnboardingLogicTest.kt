package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    @Test
    fun stepsRunWelcomeScanReel() {
        assertEquals(OnboardingStep.Scan, OnboardingStep.Welcome.next)
        assertEquals(OnboardingStep.Reel, OnboardingStep.Scan.next)
        assertNull(OnboardingStep.Reel.next)
        assertNull(OnboardingStep.Welcome.previous)
        assertEquals(OnboardingStep.Scan, OnboardingStep.Reel.previous)
    }

    @Test
    fun onlyBrandNewInstallsSeeIt() {
        assertTrue(shouldShowOnboarding(alreadyDone = false, hasLibrary = false, hasName = false))
        assertFalse(shouldShowOnboarding(alreadyDone = true, hasLibrary = false, hasName = false))
        assertFalse(shouldShowOnboarding(alreadyDone = false, hasLibrary = true, hasName = false))
        assertFalse(shouldShowOnboarding(alreadyDone = false, hasLibrary = false, hasName = true))
    }

    @Test
    fun reelWrapsAround() {
        assertEquals(0, reelIndexAt(0, 4))
        assertEquals(3, reelIndexAt(3, 4))
        assertEquals(0, reelIndexAt(4, 4))
        assertEquals(1, reelIndexAt(5, 4))
        assertEquals(0, reelIndexAt(7, 0))
    }
}
