package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val VOICE_PREFS = "cinevault_voice"
private const val KEY_ENABLED = "enabled"
private const val KEY_SHOW_HEARD = "show_heard"
private const val KEY_PHRASES = "wake_phrases"
private const val KEY_WAKE_LISTENING = "wake_listening"

/**
 * Voice (beta) settings. Off by default. "cinevault_voice" is in the Reset list,
 * so resetting preferences switches Voice back off.
 */
internal fun loadVoiceEnabled(context: Context): Boolean =
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

internal fun loadVoiceShowHeard(context: Context): Boolean =
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_HEARD, true)

internal fun loadVoiceWakeListening(context: Context): Boolean =
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WAKE_LISTENING, true)

internal fun saveVoiceWakeListening(context: Context, value: Boolean) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WAKE_LISTENING, value).apply()
}

internal fun loadVoicePhraseIds(context: Context): Set<String> {
    val saved = context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).getStringSet(KEY_PHRASES, null)
    return saved?.toSet()?.takeIf { it.isNotEmpty() } ?: defaultWakePhraseIds()
}

internal fun saveVoiceEnabled(context: Context, value: Boolean) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
}

internal fun saveVoiceShowHeard(context: Context, value: Boolean) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_HEARD, value).apply()
}

internal fun saveVoicePhraseIds(context: Context, ids: Set<String>) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_PHRASES, ids).apply()
}

/** What the person last heard-triggered, shown briefly as a pill. */
internal data class VoiceHeardEvent(val text: String, val atMs: Long)

/**
 * Live Voice state shared by Settings (which changes it) and the listener host
 * (which reacts). Compose-observable so a switch takes effect immediately.
 */
internal object VoiceRuntime {
    var enabled by mutableStateOf(false)
    var showHeard by mutableStateOf(true)
    /** "Always listen for the wake word". Only matters while Voice control is on. */
    var wakeListening by mutableStateOf(true)
    /** True while a tap-to-talk is running, so the wake listener lets go of the microphone. */
    var talkActive by mutableStateOf(false)
    var phraseIds by mutableStateOf(defaultWakePhraseIds())
    var lastHeard by mutableStateOf<VoiceHeardEvent?>(null)
    var lastError by mutableStateOf<String?>(null)
    var isListening by mutableStateOf(false)
    /** Bumped whenever the readout numbers change, so the card redraws. */
    var statsVersion by mutableStateOf(0)
    val stats = VoiceStats()

    fun loadFrom(context: Context) {
        enabled = loadVoiceEnabled(context)
        showHeard = loadVoiceShowHeard(context)
        wakeListening = loadVoiceWakeListening(context)
        phraseIds = loadVoicePhraseIds(context)
    }
}
