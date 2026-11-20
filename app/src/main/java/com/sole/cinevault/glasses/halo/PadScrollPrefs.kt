package com.sole.cinevault.glasses.halo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** +1 = the page follows the finger like a phone (Natural), -1 = the opposite (Reversed). */
internal fun scrollSign(reversed: Boolean): Float = if (reversed) -1f else 1f

/**
 * Which way two-finger scrolling on the tablet pad moves the glasses page.
 * Reversed is the default: finger down moves down the page, finger left to right brings the
 * tiles that are ahead into view. Natural is the phone style. One setting, shared everywhere.
 */
internal object PadScrollPrefs {
    private const val PREFS = "cinevault_glasses_pad"
    private const val KEY = "scroll_reversed"

    var reversed by mutableStateOf(true)
        private set

    fun load(context: Context) {
        reversed = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)
    }

    fun set(context: Context, value: Boolean) {
        reversed = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }
}
