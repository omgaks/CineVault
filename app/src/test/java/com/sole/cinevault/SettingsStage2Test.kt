package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStage2Test {
    @Test
    fun displayNameIsTrimmedCollapsedAndLimited() {
        assertEquals("Ash", sanitizeDisplayName("  Ash  "))
        assertEquals("Ash K", sanitizeDisplayName("Ash    K"))
        assertEquals("", sanitizeDisplayName("   "))
        assertEquals(MAX_DISPLAY_NAME_LENGTH, sanitizeDisplayName("x".repeat(80)).length)
    }

    @Test
    fun crashReportHasVersionDeviceAndLog() {
        val text = buildCrashReportText("2.0", "Samsung SM-X", "14 (API 34)", "boom")
        assertTrue(text.contains("App version: 2.0"))
        assertTrue(text.contains("Device: Samsung SM-X"))
        assertTrue(text.contains("Android: 14 (API 34)"))
        assertTrue(text.endsWith("boom"))
    }

    @Test
    fun crashReportSaysSoWhenLogIsEmpty() {
        assertTrue(buildCrashReportText("2.0", "d", "a", "  ").contains("No crashes logged yet."))
    }

    @Test
    fun tutorialHasElevenUniqueGuidesWithContent() {
        assertEquals(11, TUTORIAL_GUIDES.size)
        assertEquals(11, TUTORIAL_GUIDES.map { it.id }.toSet().size)
        TUTORIAL_GUIDES.forEach { guide ->
            assertTrue(guide.steps.isNotEmpty())
            assertTrue(guide.gelIndex in 0..5)
            assertTrue(guide.title.isNotBlank() && guide.tip.isNotBlank())
        }
    }

    @Test
    fun noGuideIsComingSoonAnyMore() {
        assertEquals(emptyList<String>(), TUTORIAL_GUIDES.filter { it.comingSoon }.map { it.id })
    }

    @Test
    fun resettingPreferencesNeverForgetsTheName() {
        assertFalse("cinevault_profile" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertTrue("cinevault_metadata_settings" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
    }
}
