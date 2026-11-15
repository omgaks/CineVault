package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The audio language the person last picked in the player's audio menu.
 *
 * Audio used to follow the first preferred SUBTITLE language, so wanting English subtitles on a
 * Japanese show also picked the English dub. The two are now separate: until an audio track is
 * picked by hand the old behaviour stays (so nothing changes for existing libraries), and after
 * that the picked language is preferred for every film that carries it.
 */
internal object AudioLanguagePreference {
    private const val PREFS = "cinevault_audio_language"
    private const val KEY_LANGUAGE = "preferred_audio_language"

    private var appContext: Context? = null
    private var loaded = false
    private var language by mutableStateOf<String?>(null)

    /** The remembered audio language, or null when none has been picked yet. */
    fun current(context: Context): String? {
        if (!loaded) {
            val app = context.applicationContext
            appContext = app
            language = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)
                ?.takeIf { it.isNotBlank() }
            loaded = true
        }
        return language
    }

    /** Remembers [trackLanguage] when it names a real language ("und" and blanks are ignored). */
    fun rememberPicked(trackLanguage: String?) {
        val picked = usableAudioLanguage(trackLanguage) ?: return
        language = picked
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LANGUAGE, picked)?.apply()
    }
}

/** A track language worth remembering: not blank and not the "undetermined" placeholder. */
internal fun usableAudioLanguage(trackLanguage: String?): String? =
    trackLanguage?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
