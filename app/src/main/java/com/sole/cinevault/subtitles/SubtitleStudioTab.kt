package com.sole.cinevault.subtitles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Studio destinations.
 *
 * New IA is four rooms: SOURCE / TIME / LOOK / BRAIN.
 * Legacy names stay so VideoPlayerScreen.kt compiles without a rewrite.
 * [asRoom] maps every value onto one of the four rooms.
 */
enum class SubtitleStudioTab(val label: String, val icon: ImageVector) {
    SOURCE("Source", Icons.Filled.ViewList),
    TIME("Time", Icons.Filled.Sync),
    LOOK("Look", Icons.Filled.Palette),
    BRAIN("Brain", Icons.Filled.Settings),

    TRACK("Tracks", Icons.Filled.ViewList),
    TIMING("Timing", Icons.Filled.Sync),
    APPEARANCE("Appearance", Icons.Filled.Palette),
    DUAL("Dual", Icons.Filled.SwapHoriz),
    MANAGE("Manage", Icons.Filled.Storage),
    BEHAVIOUR("Behaviour", Icons.Filled.Settings);

    fun asRoom(): SubtitleStudioTab = when (this) {
        TRACK, DUAL, MANAGE, SOURCE -> SOURCE
        TIMING, TIME -> TIME
        APPEARANCE, LOOK -> LOOK
        BEHAVIOUR, BRAIN -> BRAIN
    }
}

internal val sharedPositionPresets = listOf(
    "Low" to 0.02f,
    "Mid" to 0.16f,
    "High" to 0.30f,
    "Top" to 0.85f
)

/** Kept for any leftover import of `positionPresets`. Same values as HUD. */
internal val positionPresets = sharedPositionPresets
