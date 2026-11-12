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

    private fun protectKey(path: String): String = "x" + path.hashCode().toString(16)

    /** True when the user chose to keep an untagged title protected (no enhancement). */
    fun isKeptProtected(context: Context, path: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(protectKey(path), false)

    fun setKeptProtected(context: Context, path: String, protect: Boolean) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (protect) editor.putBoolean(protectKey(path), true) else editor.remove(protectKey(path))
        editor.apply()
    }

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
