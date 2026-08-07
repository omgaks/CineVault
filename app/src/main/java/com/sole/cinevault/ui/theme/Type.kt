package com.sole.cinevault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.sole.cinevault.R

// FEATURE: Cinzel, used for the fresh-install welcome screen's "Welcome to
// CineVault" heading (see FreshInstallWelcomeContent in Screens.kt).
// Downloaded at runtime via Android's Downloadable Fonts API rather than
// bundled as a font file — there's no font asset in this repo to bundle,
// and this is the standard, Google-documented way to use a real Google
// Font without needing one. Falls back to the system default font
// automatically if the download ever fails (no device Google Play
// Services, offline on first launch, etc.) — never a crash, worst case
// it silently looks like the "before" version of the heading.
// Certificates are Google's own, publicly documented values for
// verifying the Fonts Provider's identity — see font_certs.xml, copied
// verbatim from the official Jetpack Compose Jetchat sample.
@OptIn(ExperimentalTextApi::class)
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

@OptIn(ExperimentalTextApi::class)
val CinzelFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Cinzel"), fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

// Set of Material typography styles for CineVault. Only bodyLarge was
// previously overridden; the rest of Material3's defaults were silently in
// effect everywhere else. Filled in properly now — hero titles, screen
// titles, section headers, body copy, and metadata/badge text each get
// their own defined style instead of falling back to Material3's generic
// defaults.
//
// NOT retrofitted onto existing screens (see the UI Design & Visual
// Language pass) — every current Text() call still passes its own explicit
// fontSize, so nothing changes for existing UI just by this file changing.
// These are here for new screens/components going forward, so they don't
// each reinvent their own font-size scale from scratch.
val Typography = Typography(
    // Hero titles — Detail screen's movie title, splash screen wordmark
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    // Screen titles — TV Show hero title, Home's "Your Cinema Library"
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    // Section headers — "Overview", "Cast & Crew", "Continue Watching"
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Overview/description body text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    // Standard descriptions, list-row subtitles
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    // Metadata lines — timestamps, file info, secondary captions
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    // Badges, corner chips, tiny UI labels
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.3.sp
    )
)
