package com.sole.cinevault.subtitles

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.core.content.edit
import androidx.media3.ui.CaptionStyleCompat

// ── Per-display subtitle profiles ────────────────────────────────────────
// CineVault keeps a SEPARATE subtitle size/position/style for each
// (device type × orientation) combination, so a text size dialed in for
// the tablet in landscape doesn't leak into how subtitles look on the
// RayNeo glasses, and vice versa. Profiles are looked up and switched
// automatically — no manual "which profile am I on" picker, matching the
// spec's "When the Air 4 Pro connects, CineVault should automatically
// switch to the RayNeo subtitle profile."
//
// NOTE: Android TV isn't wired up as an entry point anywhere else in this
// codebase (no leanback/TV manifest flags, no D-pad-specific navigation),
// so DisplayProfileType.TV exists in the data model with sensible defaults
// for when it's needed, but nothing currently detects/selects it. Only
// PHONE / TABLET / EXTERNAL are ever actually chosen right now.
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

// Sensible starting point per profile, used the first time a given
// profile is ever seen (nothing saved yet). The RayNeo/external defaults
// specifically follow the spec: slightly smaller text, lower placement,
// stronger outline, reduced background opacity versus the tablet default.
fun defaultSubtitleProfileSettings(type: DisplayProfileType, isLandscape: Boolean): SubtitleProfileSettings {
    val cineVaultForeground = 0xFFFFF3D6.toInt()
    return when (type) {
        DisplayProfileType.EXTERNAL -> SubtitleProfileSettings(
            fontSizeSp = 30f,
            bottomPadding = 0.10f,
            presetName = "CineVault",
            foregroundColor = cineVaultForeground,
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.TRANSPARENT
        )
        DisplayProfileType.TV -> SubtitleProfileSettings(
            fontSizeSp = 26f,
            bottomPadding = 0.06f,
            presetName = "CineVault",
            foregroundColor = cineVaultForeground,
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.TRANSPARENT
        )
        DisplayProfileType.TABLET -> SubtitleProfileSettings(
            fontSizeSp = if (isLandscape) 18f else 16f,
            bottomPadding = 0.02f,
            presetName = "CineVault",
            foregroundColor = cineVaultForeground,
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.TRANSPARENT
        )
        DisplayProfileType.PHONE -> SubtitleProfileSettings(
            fontSizeSp = if (isLandscape) 16f else 14f,
            bottomPadding = 0.02f,
            presetName = "CineVault",
            foregroundColor = cineVaultForeground,
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.TRANSPARENT
        )
    }
}

private const val PROFILE_PREFS_NAME = "cinevault_subtitle_profiles"

fun loadSubtitleProfileSettings(context: Context, type: DisplayProfileType, isLandscape: Boolean): SubtitleProfileSettings {
    val id = displayProfileId(type, isLandscape)
    val prefs = context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE)
    val defaults = defaultSubtitleProfileSettings(type, isLandscape)
    // A profile is only considered "saved" once its font size key exists —
    // used as the presence check for the whole settings bundle, since all
    // fields are written together in saveSubtitleProfileSettings below.
    if (!prefs.contains("$id.fontSize")) return defaults
    return SubtitleProfileSettings(
        fontSizeSp = prefs.getFloat("$id.fontSize", defaults.fontSizeSp),
        bottomPadding = prefs.getFloat("$id.bottomPadding", defaults.bottomPadding),
        presetName = prefs.getString("$id.presetName", defaults.presetName) ?: defaults.presetName,
        foregroundColor = prefs.getInt("$id.foreground", defaults.foregroundColor),
        edgeType = prefs.getInt("$id.edgeType", defaults.edgeType),
        edgeColor = prefs.getInt("$id.edgeColor", defaults.edgeColor),
        backgroundColor = prefs.getInt("$id.background", defaults.backgroundColor)
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

/** Clears only the active device/orientation profile; other displays keep their tuning. */
fun clearSubtitleProfileSettings(context: Context, type: DisplayProfileType, isLandscape: Boolean) {
    val id = displayProfileId(type, isLandscape)
    context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE).edit {
        listOf("fontSize", "bottomPadding", "presetName", "foreground", "edgeType", "edgeColor", "background")
            .forEach { remove("$id.$it") }
    }
}
