package com.sole.cinevault.glasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesFilmOwnerTest {
    @Test
    fun tabletOpensFilmOnGlassesWhenGlassesAreActive() {
        assertTrue(shouldOpenFilmOnGlasses(isHostCopy = true, glassesSessionActive = true))
    }

    @Test
    fun tabletKeepsFilmWhenNoGlasses() {
        assertFalse(shouldOpenFilmOnGlasses(isHostCopy = true, glassesSessionActive = false))
    }

    @Test
    fun glassesCopyNeverHandsTheFilmBack() {
        assertFalse(shouldOpenFilmOnGlasses(isHostCopy = false, glassesSessionActive = true))
        assertFalse(shouldOpenFilmOnGlasses(isHostCopy = false, glassesSessionActive = false))
    }
}
