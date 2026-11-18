package com.sole.cinevault.audiofx

import androidx.compose.ui.graphics.Color

/**
 * Anime Night City environment palettes for Audio Studio.
 *
 * CineVault Amber Lens is the identity: amber remains the active/action
 * colour in every environment. Environment colours illuminate scenery only.
 * This model has no dependency on player, audio sessions, or DSP state.
 */
internal enum class NightCityEnvironment(
    val displayName: String,
    val skyTop: Color,
    val skyBottom: Color,
    val building: Color,
    val windowLight: Color,
    val neonPrimary: Color,
    val neonSecondary: Color,
    val atmosphericGlow: Color,
) {
    ANIME_RAINFALL(
        displayName = "Anime Rainfall",
        skyTop = Color(0xFF10172D),
        skyBottom = Color(0xFF080A13),
        building = Color(0xFF101725),
        windowLight = Color(0xFF6FC3FF),
        neonPrimary = Color(0xFFC792FF),
        neonSecondary = Color(0xFFFF6E8C),
        atmosphericGlow = Color(0xFF26365A),
    ),
    SKYLINE_AT_DUSK(
        displayName = "Skyline at Dusk",
        skyTop = Color(0xFF55345D),
        skyBottom = Color(0xFFBD6A4E),
        building = Color(0xFF25192B),
        windowLight = Color(0xFFFFC24D),
        neonPrimary = Color(0xFFFF9F6E),
        neonSecondary = Color(0xFFFF6E8C),
        atmosphericGlow = Color(0xFF854A64),
    ),
    NEO_KYOTO_MIDNIGHT(
        displayName = "Neo Kyoto Midnight",
        skyTop = Color(0xFF12132F),
        skyBottom = Color(0xFF101A2C),
        building = Color(0xFF141525),
        windowLight = Color(0xFF7CE0C3),
        neonPrimary = Color(0xFFC792FF),
        neonSecondary = Color(0xFF6FC3FF),
        atmosphericGlow = Color(0xFF38305C),
    );

    companion object {
        val signatureAmber = Color(0xFFFFC24D)
        val spaceBlack = Color(0xFF05060A)
        val glassBase = Color(0xFF10131C)

        fun fromSavedName(name: String?): NightCityEnvironment =
            entries.firstOrNull { it.name == name } ?: ANIME_RAINFALL
    }
}
