package com.sole.cinevault.subtitles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranslationResultCoordinatorTest {

    @Test
    fun matchingPendingDualLanguage_routesToSecondary() {
        assertTrue(
            shouldRouteTranslationToDualSecondary(
                dualEnabled = true,
                pendingDualLanguage = "hi",
                completedLanguage = "hi",
            )
        )
    }

    @Test
    fun languageAliasesNormalizeBeforeComparison() {
        assertTrue(
            shouldRouteTranslationToDualSecondary(
                dualEnabled = true,
                pendingDualLanguage = "Hindi",
                completedLanguage = "hi",
            )
        )
    }

    @Test
    fun dualDisabled_routesToPrimary() {
        assertFalse(
            shouldRouteTranslationToDualSecondary(
                dualEnabled = false,
                pendingDualLanguage = "hi",
                completedLanguage = "hi",
            )
        )
    }

    @Test
    fun noPendingDualRequest_routesToPrimary() {
        assertFalse(
            shouldRouteTranslationToDualSecondary(
                dualEnabled = true,
                pendingDualLanguage = null,
                completedLanguage = "hi",
            )
        )
    }

    @Test
    fun differentCompletedLanguage_routesToPrimary() {
        assertFalse(
            shouldRouteTranslationToDualSecondary(
                dualEnabled = true,
                pendingDualLanguage = "hi",
                completedLanguage = "es",
            )
        )
    }
}
