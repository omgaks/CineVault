package com.sole.cinevault.picture

import android.content.Context

/** Remembers the picture settings chosen for each title. Off is not stored. */
object PictureMemory {
    private const val PREFS = "cinevault_picture_memory"

    private fun key(path: String): String = "p" + path.hashCode().toString(16)

    fun load(context: Context, path: String): PictureSettings? =
        PictureSettingsCodec.decode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(path), null)
        )

    fun save(context: Context, path: String, settings: PictureSettings) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (settings.preset == PicturePreset.OFF) {
            editor.remove(key(path))
        } else {
            editor.putString(key(path), PictureSettingsCodec.encode(settings))
        }
        editor.apply()
    }
}
