package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DualAiTranslationCoordinatorTest {

    @Test
    fun noPendingLanguage_doesNothing() {
        var cleared = false
        var disabled = false
        var status = ""
        var translated = false

        val coordinator = DualAiTranslationCoordinator(
            getPendingLanguage = { null },
            clearPendingLanguage = { cleared = true },
            isDualEnabled = { true },
            disableDual = { disabled = true },
            setStatusText = { status = it },
            translateActive = { translated = true },
        )

        coordinator.processPendingRequest()

        assertFalse(cleared)
        assertFalse(disabled)
        assertEquals("", status)
        assertFalse(translated)
    }

    @Test
    fun dualAlreadyDisabled_clearsPendingWithoutTranslating() {
        var pending: String? = "hi"
        var translated = false

        val coordinator = DualAiTranslationCoordinator(
            getPendingLanguage = { pending },
            clearPendingLanguage = { pending = null },
            isDualEnabled = { false },
            disableDual = {},
            setStatusText = {},
            translateActive = { translated = true },
        )

        coordinator.processPendingRequest()

        assertNull(pending)
        assertFalse(translated)
    }

    @Test
    fun unsupportedLanguage_disablesDualAndClearsPending() {
        var pending: String? = "zz-unsupported"
        var dualEnabled = true
        var status = ""
        var translated = false

        val coordinator = DualAiTranslationCoordinator(
            getPendingLanguage = { pending },
            clearPendingLanguage = { pending = null },
            isDualEnabled = { dualEnabled },
            disableDual = { dualEnabled = false },
            setStatusText = { status = it },
            translateActive = { translated = true },
            resolveTarget = { null },
        )

        coordinator.processPendingRequest()

        assertNull(pending)
        assertFalse(dualEnabled)
        assertTrue(status.contains("AI translation isn't available"))
        assertFalse(translated)
    }

    @Test
    fun supportedLanguage_startsTranslationAndKeepsPendingUntilResult() {
        val target = SubtitleTranslationEngine.commonTargetLanguages.first()
        var pending: String? = target.mlKitCode
        var translatedTarget: SubtitleTranslationEngine.SupportedLanguage? = null

        val coordinator = DualAiTranslationCoordinator(
            getPendingLanguage = { pending },
            clearPendingLanguage = { pending = null },
            isDualEnabled = { true },
            disableDual = {},
            setStatusText = {},
            translateActive = { translatedTarget = it },
            resolveTarget = { target },
        )

        coordinator.processPendingRequest()

        assertSame(target, translatedTarget)
        assertEquals(target.mlKitCode, pending)
    }

    @Test
    fun resolver_matchesKnownMlKitCode() {
        val target = SubtitleTranslationEngine.commonTargetLanguages.first()

        val resolved = resolveDualAiTranslationTarget(target.mlKitCode)

        assertEquals(target.mlKitCode, resolved?.mlKitCode)
    }
}
