package com.sole.cinevault.subtitles

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.core.content.edit
import androidx.media3.ui.CaptionStyleCompat

// Separate subtitle settings are retained per display role and orientation.
// External-display tuning never leaks into the host display and vice versa.
// Selection is automatic and contains no hardware-vendor-specific profile.
enum class DisplayProfileType(val label: String) {
    PHONE("Phone"), TABLET("Tablet"), TV("Android TV"), EXTERNAL("External Display")
}

fun displayProfileId(type: DisplayProfileType, isLandscape: Boolean): String =
    "${type.name.lowercase()}_${if (isLandscape) "landscape" else "portrait"}"

data class SubtitleProfileSettings(
    val fontSizeSp: Float,
    val bottomPadding: Float,
    val presetName: String,
    val foregroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    val backgroundColor: Int
)

fun defaultSubtitleProfileSettings(type: DisplayProfileType, isLandscape: Boolean): SubtitleProfileSettings {
    val foreground = 0xFFFFF3D6.toInt()
    return when (type) {
        DisplayProfileType.EXTERNAL -> SubtitleProfileSettings(30f, 0.10f, "CineVault", foreground, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
        DisplayProfileType.TV -> SubtitleProfileSettings(26f, 0.06f, "CineVault", foreground, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
        DisplayProfileType.TABLET -> SubtitleProfileSettings(if (isLandscape) 18f else 16f, 0.02f, "CineVault", foreground, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
        DisplayProfileType.PHONE -> SubtitleProfileSettings(if (isLandscape) 16f else 14f, 0.02f, "CineVault", foreground, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, AndroidColor.TRANSPARENT)
    }
}

private const val PROFILE_PREFS_NAME = "cinevault_subtitle_profiles"

fun loadSubtitleProfileSettings(context: Context, type: DisplayProfileType, isLandscape: Boolean): SubtitleProfileSettings {
    val id = displayProfileId(type, isLandscape)
    val prefs = context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE)
    val defaults = defaultSubtitleProfileSettings(type, isLandscape)
    if (!prefs.contains("$id.fontSize")) return defaults
    return SubtitleProfileSettings(
        prefs.getFloat("$id.fontSize", defaults.fontSizeSp),
        prefs.getFloat("$id.bottomPadding", defaults.bottomPadding),
        prefs.getString("$id.presetName", defaults.presetName) ?: defaults.presetName,
        prefs.getInt("$id.foreground", defaults.foregroundColor),
        prefs.getInt("$id.edgeType", defaults.edgeType),
        prefs.getInt("$id.edgeColor", defaults.edgeColor),
        prefs.getInt("$id.background", defaults.backgroundColor)
    )
}

fun saveSubtitleProfileSettings(context: Context, type: DisplayProfileType, isLandscape: Boolean, settings: SubtitleProfileSettings) {
    val id = displayProfileId(type, isLandscape)
    context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE).edit {
        putFloat("$id.fontSize", settings.fontSizeSp)
        putFloat("$id.bottomPadding", settings.bottomPadding)
        putString("$id.presetName", settings.presetName)
        putInt("$id.foreground", settings.foregroundColor)
        putInt("$id.edgeType", settings.edgeType)
        putInt("$id.edgeColor", settings.edgeColor)
        putInt("$id.background", settings.backgroundColor)
    }
}

fun clearSubtitleProfileSettings(context: Context, type: DisplayProfileType, isLandscape: Boolean) {
    val id = displayProfileId(type, isLandscape)
    context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE).edit {
        listOf("fontSize","bottomPadding","presetName","foreground","edgeType","edgeColor","background")
            .forEach { remove("$id.$it") }
    }
}
