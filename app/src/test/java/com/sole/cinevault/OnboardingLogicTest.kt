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

    @Test
    fun wallPrefersPostersAndSkipsRepeats() {
        val slots = posterWallSlots(
            listOf("A" to null, "B" to "p1", "B again" to "p1", "C" to "p2", "A" to null, "D" to ""),
            count = 4
        )
        assertEquals(listOf("B", "C", "A", "D"), slots.map { it.title })
        assertEquals(listOf("p1", "p2", null, null), slots.map { it.posterUrl })
    }

    @Test
    fun wallStopsAtCountAndIgnoresBlankTitles() {
        val slots = posterWallSlots(listOf("" to "p0", "X" to "p1", "Y" to "p2"), count = 1)
        assertEquals(listOf("X"), slots.map { it.title })
    }

    @Test
    fun orbitFrontFollowsAngle() {
        assertEquals(0, orbitFrontIndex(90f, 8))
        assertEquals(1, orbitFrontIndex(45f, 8))
        assertEquals(7, orbitFrontIndex(135f, 8))
        assertEquals(0, orbitFrontIndex(90f + 360f, 8))
        assertEquals(0, orbitFrontIndex(10f, 0))
    }

    @Test
    fun orbitTurnTakesTheShortWay() {
        assertEquals(0f, orbitTurnToFront(90f, 0, 8), 0.001f)
        assertEquals(-45f, orbitTurnToFront(90f, 1, 8) , 0.001f)
        val turn = orbitTurnToFront(350f, 0, 8)
        assertTrue(turn in -180f..180f)
    }
}
