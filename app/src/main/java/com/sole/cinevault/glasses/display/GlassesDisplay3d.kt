package com.sole.cinevault.glasses.display

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "My glasses are in their own 3D display mode."
 *
 * In that mode the glasses send the left half of the screen to the left eye and the right
 * half to the right eye. A 3D film already fits that, but the normal app would look split in
 * two. With this on, the app is drawn once per eye so it looks normal; a 3D film still gets
 * the whole frame. The app cannot see the glasses' own switch, so this is a setting.
 */
internal object GlassesDisplay3d {
    private const val PREFS = "cinevault_glasses_pad"
    private const val KEY = "display_3d"

    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        enabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }
}

/** The app is drawn once per eye except while a 3D film owns the full frame. */
internal fun shouldSplitForEyes(display3dOn: Boolean, fullFrame3dFilm: Boolean): Boolean =
    display3dOn && !fullFrame3dFilm
