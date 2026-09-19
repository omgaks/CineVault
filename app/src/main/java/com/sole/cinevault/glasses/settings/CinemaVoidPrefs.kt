package com.sole.cinevault.glasses.settings

import android.content.Context

private const val PREFS = "cinevault_glasses_settings"
private const val KEY_CINEMA_VOID = "cinema_void_enabled"
private const val KEY_CINEMA_VOID_SUGGESTED = "cinema_void_suggested"

fun isCinemaVoidEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_CINEMA_VOID, false)

fun setCinemaVoidEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_CINEMA_VOID, enabled)
        .apply()
}

fun hasCinemaVoidBeenSuggested(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_CINEMA_VOID_SUGGESTED, false)

fun markCinemaVoidSuggested(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_CINEMA_VOID_SUGGESTED, true)
        .apply()
}
