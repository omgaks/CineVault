package com.sole.cinevault.ui.responsive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

/*
 * WindowSizeClass.kt
 *
 * Project-wide responsive-UI foundation. Every CineVault screen and
 * component is expected to size and lay itself out from
 * rememberCineWindowSizeInfo() (or the dimension helpers below it) rather
 * than fixed dp values, so the app adapts correctly across: small/
 * standard/large phones, 7"-12"+ tablets, portrait/landscape, foldables,
 * Android split-screen and freeform windows, TV/external displays, and
 * different densities/font scales. This is a standing requirement, not a
 * one-time pass — see RESPONSIVE_DESIGN.md (repo root, alongside README.md
 * and PRIVACY.md) for the full policy this file implements.
 *
 * DELIBERATELY BUILT WITHOUT THE androidx.window LIBRARY: this project has
 * no dependency on androidx.window / material3-window-size-class today,
 * and adding one is a real Gradle/version-resolution change that cannot be
 * verified as compiling correctly without a real build environment — the
 * same caution already applied to every other dependency decision in this
 * project. Configuration.screenWidthDp/screenHeightDp (via
 * LocalConfiguration, already used elsewhere in this app) give the exact
 * same practical information for this purpose, using only APIs already in
 * this project's dependency graph:
 *
 *   - They reflect the app's ACTUAL CURRENT WINDOW, not the physical
 *     display — this is what makes split-screen and freeform window
 *     support work correctly. A phone in split-screen at 340dp wide reports
 *     screenWidthDp=340 here, not the device's full 400dp+ screen width.
 *   - They already update live across rotation, folding/unfolding a
 *     foldable, and window resizes, since Configuration itself is what
 *     changes and triggers recomposition through LocalConfiguration.
 *
 * The three width/height classes below (Compact / Medium / Expanded) use
 * the exact same dp breakpoints Google's own Material 3 window size class
 * guidance defines (m3.material.io/foundations/layout/applying-layout/
 * window-size-classes) — a widely-used, well-established standard, not a
 * value invented for this project. Matching it means these categories mean
 * the same thing here as they do in any other Android app or in Google's
 * own documentation, which matters if androidx.window is ever adopted
 * later: the category BOUNDARIES already match, so nothing here would need
 * to be reinterpreted, only re-plumbed onto the official types if desired.
 */

enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }
enum class WindowHeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * A snapshot of the app's actual current window, read fresh on every
 * recomposition (Configuration changes trigger recomposition through
 * LocalConfiguration automatically — no manual listener needed).
 */
data class CineWindowSizeInfo(
    val widthDp: Dp,
    val heightDp: Dp,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val isPortrait: Boolean,
    /**
     * Android's own long-standing resource-qualifier convention
     * (sw600dp) for what counts as a tablet-class device — the
     * SMALLER of width/height must be at least 600dp, so a phone
     * rotated to landscape (e.g. 780dp wide but only 360dp tall)
     * still correctly reads as NOT a tablet, only a wide phone window.
     */
    val isTabletClass: Boolean,
    /**
     * The system font-scale multiplier the user has chosen
     * (Settings > Display > Font size). 1.0 = default. CineVault
     * screens must remain usable, not clipped or overlapping, at
     * larger values — this is exposed so a screen can make an
     * informed layout choice (e.g. switching a Row to a Column) at
     * large scale factors rather than only relying on text
     * reflowing on its own, which isn't always enough.
     */
    val fontScale: Float,
)

/**
 * The single entry point every CineVault screen/component should read
 * window information from. Cheap to call from anywhere (reads
 * LocalConfiguration, which is already provided at the root of every
 * Compose hierarchy) — no need to hoist this and pass it down manually.
 */
@Composable
@ReadOnlyComposable
fun rememberCineWindowSizeInfo(): CineWindowSizeInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp

    val widthClass = when {
        configuration.screenWidthDp < 600 -> WindowWidthClass.COMPACT
        configuration.screenWidthDp < 840 -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    val heightClass = when {
        configuration.screenHeightDp < 480 -> WindowHeightClass.COMPACT
        configuration.screenHeightDp < 900 -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }

    val smallestDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)

    return CineWindowSizeInfo(
        widthDp = widthDp,
        heightDp = heightDp,
        widthClass = widthClass,
        heightClass = heightClass,
        isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
        isTabletClass = smallestDp >= 600,
        fontScale = configuration.fontScale,
    )
}

/**
 * Reusable adaptive-dimension decisions built on CineWindowSizeInfo, so
 * individual screens make the same calls for the same situations rather
 * than each screen inventing its own breakpoint math. Add to this object
 * as new shared patterns emerge — it's meant to grow, not to be a fixed,
 * closed set.
 */
object CineResponsive {

    /**
     * A sensible max width for a floating popup/dialog/dashboard
     * (subtitle menus, the Audio FX dashboard, etc.) — narrow enough to
     * stay readable and hand-reachable on a phone, wide enough to not
     * look like a stranded postage stamp in the middle of a 12" tablet
     * or an expanded split-screen pane. Callers should still combine
     * this with .widthIn(max = ...) rather than a bare fillMaxWidth(),
     * since a popup filling 100% of an expanded window is rarely
     * correct either.
     */
    fun popupMaxWidth(info: CineWindowSizeInfo): Dp = when (info.widthClass) {
        WindowWidthClass.COMPACT -> minOf(info.widthDp * 0.92f, 360.dp)
        WindowWidthClass.MEDIUM -> 420.dp
        WindowWidthClass.EXPANDED -> 480.dp
    }

    /**
     * Horizontal content padding/inset for a full-screen layout (Home,
     * Library, Search grids, etc.) — a phone can afford to use nearly
     * its whole width, but content stretched edge-to-edge on a large
     * tablet or an expanded desktop-style window reads as unfinished,
     * not as "using the space well."
     */
    fun screenHorizontalPadding(info: CineWindowSizeInfo): Dp = when (info.widthClass) {
        WindowWidthClass.COMPACT -> 16.dp
        WindowWidthClass.MEDIUM -> 32.dp
        WindowWidthClass.EXPANDED -> 48.dp
    }

    /**
     * A reasonable poster/tile grid column count for the current window.
     * Deliberately conservative (doesn't scale linearly with raw dp) —
     * more columns only helps if each tile stays a sensible, still-
     * legible size, not just "as many as technically fit."
     */
    fun gridColumns(info: CineWindowSizeInfo): Int = when {
        info.widthClass == WindowWidthClass.COMPACT && info.isPortrait -> 3
        info.widthClass == WindowWidthClass.COMPACT -> 4
        info.widthClass == WindowWidthClass.MEDIUM -> 5
        else -> 6
    }

    /**
     * Whether a two-pane / master-detail layout (a list alongside its
     * detail view, rather than navigating between two full screens)
     * makes sense at the current window size. True from Medium width
     * up — a folded/compact phone should still navigate between
     * screens rather than cramming two panes into ~360dp.
     */
    fun useTwoPaneLayout(info: CineWindowSizeInfo): Boolean =
        info.widthClass != WindowWidthClass.COMPACT
}
