package com.sole.cinevault

import android.content.Context

/**
 * Resets user-tunable CineVault preferences without touching the library,
 * watch history, favourites, secret videos, selected folders, downloaded/AI
 * subtitle files, metadata cache, SMB credentials, or other user media/data.
 */
internal val RESETTABLE_CINEVAULT_PREFERENCE_FILES = listOf(
    "cinevault_subtitle_behavior",
    "cinevault_subtitle_cleaning",
    "cinevault_subtitle_profiles",
    "dual_subtitle_language_preferences",
    "ai_subtitle_language_preferences",
    "cinevault_audio_fx",
    "cinevault_glasses_settings",
    "cinevault_glasses_calibration",
    "cinevault_metadata_settings",
    "cinevault_voice",
)

internal fun resetCineVaultSettings(context: Context) {
    RESETTABLE_CINEVAULT_PREFERENCE_FILES.forEach { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
