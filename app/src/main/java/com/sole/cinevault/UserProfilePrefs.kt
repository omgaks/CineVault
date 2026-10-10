package com.sole.cinevault

import android.content.Context

/**
 * The optional name shown in Home greetings ("Good evening, Ash.").
 * Stored only on this device. Deliberately NOT part of the Reset list,
 * so resetting preferences never forgets who you are.
 */
private const val PROFILE_PREFS = "cinevault_profile"
private const val KEY_DISPLAY_NAME = "display_name"

internal const val MAX_DISPLAY_NAME_LENGTH = 24

/** Trims, collapses repeated spaces and limits the length. Blank means "no name". */
internal fun sanitizeDisplayName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").take(MAX_DISPLAY_NAME_LENGTH).trim()

internal fun loadDisplayName(context: Context): String =
    sanitizeDisplayName(
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_NAME, "") ?: ""
    )

internal fun saveDisplayName(context: Context, raw: String) {
    context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_DISPLAY_NAME, sanitizeDisplayName(raw)).apply()
}
