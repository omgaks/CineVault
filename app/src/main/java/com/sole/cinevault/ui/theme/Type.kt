package com.sole.cinevault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sole.cinevault.R

// FEATURE: Cinzel, used for the fresh-install welcome screen's "Welcome to
// CineVault" heading (see FreshInstallWelcomeContent in Screens.kt).
// Bundled locally (app/src/main/res/font/cinzel_bold.ttf) rather than
// fetched at runtime via Google Play Services' Downloadable Fonts API —
// simpler, no dependency on Play Services being available, no first-launch
// network fetch delay. Font file downloaded directly from
// fonts.google.com/specimen/Cinzel.
val CinzelFontFamily = FontFamily(
    Font(resId = R.font.cinzel_bold, weight = FontWeight.Bold, style = FontStyle.Normal, loadingStrategy = FontLoadingStrategy.Blocking)
)


// ------------------------------------------------------------
// V3 FONTS (design kit): Newsreader for titles, Manrope for everything
// you tap and read, IBM Plex Mono for numbers and small labels.
// All bundled locally (OFL licence), same reasoning as Cinzel above.
// ------------------------------------------------------------
val NewsreaderFamily = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic)
)
val ManropeFamily = FontFamily(
    Font(R.font.manrope_medium, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_bold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black)
)
val PlexMonoFamily = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium)
)

// ------------------------------------------------------------
// V3 TEXT SCALE — one scale for the whole app. Nothing below 12sp.
// All values are sp, so they follow the user's Android font size.
// ------------------------------------------------------------
object CineType {
    val MinSp: TextUnit = 12.sp
    val Caption: TextUnit = 12.sp   // hints, badges, chips
    val Label: TextUnit = 13.sp     // buttons, pill labels
    val Body: TextUnit = 15.sp      // descriptions, rows
    val Title: TextUnit = 18.sp     // card and row titles
    val Section: TextUnit = 22.sp   // shelf headings (Newsreader)
    val Display: TextUnit = 28.sp   // film titles, dialogs (Newsreader)
    val Hero: TextUnit = 44.sp      // screen titles (Newsreader)

    /** Never returns less than [MinSp]; use when a size is computed. */
    fun atLeastMin(size: TextUnit): TextUnit = if (size.value < MinSp.value) MinSp else size
}

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
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    // Screen titles — TV Show hero title, Home's "Your Cinema Library"
    displayMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    // Section headers — "Overview", "Cast & Crew", "Continue Watching"
    displaySmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Overview/description body text
    bodyLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    // Standard descriptions, list-row subtitles
    bodyMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    // Metadata lines — timestamps, file info, secondary captions
    bodySmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    // Badges, corner chips, tiny UI labels
    labelSmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)
