package com.sole.cinevault

import android.content.Context

/**
 * Tiny local store for the compatibility matrix.
 *
 * SharedPreferences is sufficient here because the matrix is capped at 200
 * compact records and updates are infrequent compared with normal playback.
 */
class PlaybackCompatibilityStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(
        device: PlaybackCompatibilityDevice,
    ): List<PlaybackCompatibilityMatrixEntry> =
        decodePlaybackCompatibilityEntries(
            preferences.getString(KEY_ENTRIES, null)
        ).filter { it.device == device }

    fun save(
        entries: List<PlaybackCompatibilityMatrixEntry>,
    ) {
        preferences.edit()
            .putString(
                KEY_ENTRIES,
                encodePlaybackCompatibilityEntries(entries),
            )
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_ENTRIES)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "cinevault_playback_compatibility"
        const val KEY_ENTRIES = "matrix_entries_v1"
    }
}
