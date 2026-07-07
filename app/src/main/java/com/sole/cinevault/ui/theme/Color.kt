package com.sole.cinevault.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
//  CineVault 2 — "Space Glass" Design System
//  Deep space surfaces + frosted glass panels + amber glow
// ============================================================

// ------------------------------------------------------------
// LEGACY v1 TOKENS — kept so all existing screens still compile.
// New code should prefer the v2 tokens below.
// ------------------------------------------------------------
val CineBackground    = Color(0xFF080808)
val CineCard          = Color(0xFF131313)
val CineGlass         = Color(0x55181818)
val CineAccent        = Color(0xFFE8A020)   // warm amber
val CineAccentDim     = Color(0xFFB07818)
val CineTextPrimary   = Color.White
val CineTextSecondary = Color(0xFFAAAAAA)
val CineSurface       = Color(0xFF1A1A1A)

// ------------------------------------------------------------
// V2 — SPACE LAYER (backgrounds)
// Near-black with a whisper of blue, like deep space —
// makes the amber glow pop harder than pure black.
// ------------------------------------------------------------
val SpaceBlack = Color(0xFF05060A)   // deepest layer (screen background)
val SpaceDeep  = Color(0xFF0A0C12)   // gradient partner for backgrounds
val SpaceMid   = Color(0xFF10131C)   // elevated solid surfaces (sheets, dialogs)

// ------------------------------------------------------------
// V2 — GLASS LAYER (frosted panels)
// Translucent fills + gradient borders = the glass illusion.
// ------------------------------------------------------------
val GlassSurface       = Color(0x66141822)  // standard frosted panel fill
val GlassSurfaceStrong = Color(0x99141822)  // heavier glass (control decks)
val GlassSurfaceFaint  = Color(0x33141822)  // barely-there glass (overlays)
val GlassBorderTop     = Color(0x40FFFFFF)  // light catches the top edge
val GlassBorderBottom  = Color(0x12FFFFFF)  // fades out at the bottom
val GlassHighlight     = Color(0x14FFFFFF)  // inner sheen wash

// ------------------------------------------------------------
// V2 — AMBER GLOW LAYER (the CineVault signature)
// ------------------------------------------------------------
val AmberCore  = Color(0xFFFFC24D)  // hottest center of a glow / active text
val AmberGlow  = Color(0xFFE8A020)  // primary accent (same as CineAccent)
val AmberDeep  = Color(0xFFB07818)  // dimmed accent
val AmberEmber = Color(0xFF6E4A10)  // outer falloff of glows

// ------------------------------------------------------------
// V2 — TEXT
// ------------------------------------------------------------
val TextBright = Color(0xFFF5F3EE)  // warm white — softer than pure white on glass
val TextMuted  = Color(0xFFA8A6A0)  // secondary text
val TextFaint  = Color(0xFF6B6A66)  // timestamps, hints
