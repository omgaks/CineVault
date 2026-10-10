package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsResetTest {
    @Test
    fun resetScopeIncludesUserTunablePlayerPreferences() {
        assertTrue("cinevault_subtitle_behavior" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertTrue("cinevault_subtitle_profiles" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertTrue("cinevault_audio_fx" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertTrue("cinevault_metadata_settings" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertTrue("cinevault_voice" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
    }

    @Test
    fun resetScopeDoesNotIncludeUserLibraryOrPrivateData() {
        assertFalse("cinevault_restricted_folders" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertFalse("cinevault_favorites" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertFalse("cinevault_secret_secure" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertFalse("cinevault_movie_subtitle_memory" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
        assertFalse("cinevault_smb_shares_secure" in RESETTABLE_CINEVAULT_PREFERENCE_FILES)
    }
}
