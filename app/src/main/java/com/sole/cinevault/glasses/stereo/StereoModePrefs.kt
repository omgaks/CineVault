package com.sole.cinevault.glasses.stereo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Remembers the 3D choice. State-backed so changing it in Settings updates a playing film. */
internal object StereoModePrefs {
    private const val PREFS = "cinevault_glasses_stereo"
    private const val KEY = "mode"

    private var loaded = false
    var choice by mutableStateOf(StereoModeChoice.AUTO)
        private set

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        choice = StereoModeChoice.fromStored(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )
    }

    fun set(context: Context, value: StereoModeChoice) {
        loaded = true
        choice = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.stored).apply()
    }
}
