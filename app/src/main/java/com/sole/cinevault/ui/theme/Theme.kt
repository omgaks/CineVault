package com.sole.cinevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
//  CineVault 2 Theme — always dark. Cinema doesn't do daylight.
//  Dynamic color removed on purpose: the space-glass identity
//  must never be overridden by the device wallpaper palette.
// ============================================================

private val CineVaultColorScheme = darkColorScheme(
    primary = AmberGlow,
    secondary = AmberDeep,
    tertiary = AmberCore,

    background = SpaceBlack,
    surface = SpaceMid,
    surfaceVariant = SpaceDeep,

    onPrimary = Color.Black,        // amber is bright — dark text reads better on it
    onSecondary = Color.White,
    onBackground = TextBright,
    onSurface = TextBright,
    onSurfaceVariant = TextMuted,

    outline = GlassBorderTop,
    scrim = Color(0xCC05060A)
)

@Composable
fun CineVaultTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,   // kept in the signature so existing calls compile; ignored
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CineVaultColorScheme,
        typography = Typography,
        content = content
    )
}
