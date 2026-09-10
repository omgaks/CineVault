package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVideoSessionInitializationTest {

    private fun eligible(
        isStreamMedia: Boolean = false,
        canDownloadExternalSubtitles: Boolean = true,
        isRestrictedFolderMedia: Boolean = false,
        autoDownloadWhenMissing: Boolean = true,
        restoredRememberedPrimary: Boolean = false,
        hasCachedSubtitle: Boolean = false,
        hasLocalMatch: Boolean = false,
        alreadyAttemptedForPath: Boolean = false,
    ) = shouldAutoDownloadSubtitleOnSessionStart(
        isStreamMedia = isStreamMedia,
        canDownloadExternalSubtitles = canDownloadExternalSubtitles,
        isRestrictedFolderMedia = isRestrictedFolderMedia,
        autoDownloadWhenMissing = autoDownloadWhenMissing,
        restoredRememberedPrimary = restoredRememberedPrimary,
        hasCachedSubtitle = hasCachedSubtitle,
        hasLocalMatch = hasLocalMatch,
        alreadyAttemptedForPath = alreadyAttemptedForPath,
    )

    @Test
    fun completelyMissingSubtitle_isEligibleForAutoDownload() {
        assertTrue(eligible())
    }

    @Test
    fun streamMedia_isNotEligible() {
        assertFalse(eligible(isStreamMedia = true))
    }

    @Test
    fun restrictedMedia_isNotEligible() {
        assertFalse(eligible(isRestrictedFolderMedia = true))
    }

    @Test
    fun rememberedPrimary_isNotEligible() {
        assertFalse(eligible(restoredRememberedPrimary = true))
    }

    @Test
    fun localMatch_isNotEligible() {
        assertFalse(eligible(hasLocalMatch = true))
    }

    @Test
    fun cachedSubtitle_isNotEligible() {
        assertFalse(eligible(hasCachedSubtitle = true))
    }

    @Test
    fun alreadyAttempted_isNotEligible() {
        assertFalse(eligible(alreadyAttemptedForPath = true))
    }

    @Test
    fun userDisabledAutoDownload_isNotEligible() {
        assertFalse(eligible(autoDownloadWhenMissing = false))
    }

    @Test
    fun unsupportedMediaType_isNotEligible() {
        assertFalse(eligible(canDownloadExternalSubtitles = false))
    }
}
