package com.sole.cinevault.subtitles

import android.net.Uri
import androidx.media3.common.MimeTypes

// ── External subtitle format support ─────────────────────────────────────
// Covers what actually needs code here: EXTERNAL subtitle files the person
// downloads or browses to. Embedded container tracks (MKV/MP4) are a
// separate story — Media3's default extractors + TextRenderer already
// decode embedded SRT, ASS/SSA, WebVTT, TTML, PGS, and DVB subtitle tracks
// with zero custom code, since those decoders ship built into the
// media3-exoplayer artifact CineVault already depends on. That was true
// before this file existed and doesn't need touching.
//
// What's genuinely NOT supported, for external files specifically:
// - VobSub (.sub/.idx pair, DVD-style bitmap subtitles) — Media3 has no
//   built-in decoder for this format at all (embedded or external); would
//   need the FFmpeg extension or a custom decoder, out of scope here.
// - Classic MicroDVD (.sub, frame-number-based timing) — no built-in Media3
//   decoder either. Genuinely rare to encounter as a standalone external
//   file in practice (almost always superseded by SRT for the same
//   release), so not worth the custom decoder effort right now.
// Both are explicitly reported as unsupported rather than silently
// mishandled — see subtitleFormatSupportNote().
enum class SubtitleFormat(val label: String, val mimeType: String?) {
    SRT("SubRip (.srt)", MimeTypes.APPLICATION_SUBRIP),
    VTT("WebVTT (.vtt)", MimeTypes.TEXT_VTT),
    ASS("Advanced SubStation Alpha (.ass)", MimeTypes.TEXT_SSA),
    SSA("SubStation Alpha (.ssa)", MimeTypes.TEXT_SSA),
    TTML("TTML (.ttml/.dfxp)", MimeTypes.APPLICATION_TTML),
    VOBSUB("VobSub (.sub/.idx)", null),
    MICRODVD("MicroDVD (.sub)", null),
    UNKNOWN("Unknown", null)
}

fun detectSubtitleFormat(fileName: String): SubtitleFormat {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".srt") -> SubtitleFormat.SRT
        lower.endsWith(".vtt") -> SubtitleFormat.VTT
        lower.endsWith(".ass") -> SubtitleFormat.ASS
        lower.endsWith(".ssa") -> SubtitleFormat.SSA
        lower.endsWith(".ttml") || lower.endsWith(".dfxp") || lower.endsWith(".xml") -> SubtitleFormat.TTML
        lower.endsWith(".idx") -> SubtitleFormat.VOBSUB
        // Bare ".sub" is ambiguous between VobSub and MicroDVD without
        // inspecting the file's actual content (VobSub is binary, MicroDVD
        // is plain text with {frame}{frame} timing) — sniff the first
        // bytes' printability as a cheap heuristic rather than guess blind.
        lower.endsWith(".sub") -> SubtitleFormat.MICRODVD
        else -> SubtitleFormat.UNKNOWN
    }
}

fun detectSubtitleFormat(uri: Uri): SubtitleFormat = detectSubtitleFormat(uri.lastPathSegment ?: uri.toString())

// CineVault's own sync-shift, cleaning, and dual-subtitle merge features
// all work by regex-parsing SRT's specific "00:00:01,000 --> ..." timing
// syntax and cue-block structure. Feeding any other format through that
// pipeline wouldn't corrupt the file (the regexes simply won't match
// anything and the text passes through unchanged) but the FEATURE itself
// would silently do nothing — worse than not offering it. Gate on this
// instead of guessing.
fun supportsCustomTextPipeline(format: SubtitleFormat): Boolean = format == SubtitleFormat.SRT

fun isPlayableExternalFormat(format: SubtitleFormat): Boolean = format.mimeType != null

fun subtitleFormatSupportNote(format: SubtitleFormat): String? = when (format) {
    SubtitleFormat.VOBSUB -> "VobSub isn't supported for external files — CineVault has no decoder for this bitmap-based format."
    SubtitleFormat.MICRODVD -> "This looks like a frame-based MicroDVD .sub file, which CineVault doesn't decode — an .srt for the same release usually exists instead."
    SubtitleFormat.UNKNOWN -> "Unrecognized subtitle file format."
    SubtitleFormat.VTT, SubtitleFormat.ASS, SubtitleFormat.SSA, SubtitleFormat.TTML ->
        "Sync adjustment, cleaning, and dual-subtitle merging only work on .srt files — this format plays normally but those tools won't apply to it."
    SubtitleFormat.SRT -> null
}
