package com.sole.cinevault

// ── Subtitle Cleaning ─────────────────────────────────────────────────────
// Pure text transforms over an already-parsed .srt file. Deliberately kept
// separate from timing logic (VideoPlayerScreen.kt's shiftSrtTimestampMatch)
// — cleaning only ever touches the TEXT lines of each cue block, never the
// index or timestamp lines, so it composes safely with sync/drift shifting
// regardless of which runs first.
//
// LIMITATION: this only works on external subtitle files (downloaded SRTs,
// local .srt files) since it rewrites the actual file text before handing
// it to ExoPlayer. Embedded subtitle tracks are parsed by ExoPlayer/Media3
// directly from the container and can't be rewritten this way without
// extracting and re-injecting the track, which is out of scope here.
data class SubtitleCleaningOptions(
    val hideHearingImpairedDescriptions: Boolean = false,
    val removeSpeakerNames: Boolean = false,
    val fixBrokenLineBreaks: Boolean = false,
    val mergeVeryShortLines: Boolean = false,
    val correctEncodingSymbols: Boolean = false,
    val removeHtmlTags: Boolean = false,
    val convertAllCaps: Boolean = false,
    val removeDuplicateLines: Boolean = false
) {
    val isAnyEnabled: Boolean
        get() = hideHearingImpairedDescriptions || removeSpeakerNames || fixBrokenLineBreaks ||
                mergeVeryShortLines || correctEncodingSymbols || removeHtmlTags || convertAllCaps || removeDuplicateLines
}

internal data class SrtBlock(val index: String, val timing: String, val lines: List<String>)

// Normalizes a raw timing line to spec-correct comma-separated SRT
// ("00:00:01,000 --> 00:00:04,000"), regardless of whether the source used
// comma (SRT) or dot (VTT) — needed because cleanSrtText/mergeDualSubtitles
// both re-emit block.timing essentially verbatim otherwise, and elsewhere
// in this codebase every file this pipeline produces is documented and
// relied upon as "always genuine comma-SRT" (see VideoPlayerScreen.kt's
// playCurrentVideoWithSubtitle, which picks the SubRip MIME type on that
// assumption). A VTT file's dot-separated timestamps passing through
// unchanged would silently break that assumption — the output would be
// labeled/served as SubRip but not actually be valid SubRip, which a
// strict decoder could simply fail to parse, rendering NO subtitles at
// all with no visible error. Also strips any trailing VTT-only cue
// settings (e.g. "align:start line:90%") that can follow the end
// timestamp — CineVault's own subtitle positioning is handled separately
// via SubtitleAppearance, so this text has no use here and isn't
// guaranteed safe for a strict SRT parser to see.
private val TIMING_LINE_REGEX = Regex("(\\d{2}:\\d{2}:\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2})[,.](\\d{3})")
internal fun normalizeTimingLine(timing: String): String {
    val match = TIMING_LINE_REGEX.find(timing) ?: return timing
    val (startTime, startMs, endTime, endMs) = match.destructured
    return "$startTime,$startMs --> $endTime,$endMs"
}

// Sound-cue / music notation: [MUSIC PLAYING], (door opens), ♪ lines, etc.
// Matches a line that's ENTIRELY bracketed content, not just contains
// brackets somewhere (so "I saw him [pause] leave" — a real mid-sentence
// aside — isn't nuked, only whole-line HI descriptions are).
private val wholeLineBracketRegex = Regex("^\\s*[\\[(【][^\\])】]*[\\])】]\\s*$")
private val musicNoteRegex = Regex("^\\s*[♪♫]+.*[♪♫]*\\s*$")

// Speaker label at the start of a line: "JOHN:", "MARY (O.S.):", etc. — all
// caps (or all caps + parenthetical) immediately followed by a colon.
private val speakerLabelRegex = Regex("^\\s*[A-Z][A-Z0-9 .'\\-]{1,24}(\\s*\\([^)]*\\))?\\s*:\\s*")

private val htmlTagRegex = Regex("</?[a-zA-Z][^>]*>")

// Common mis-decoded UTF-8-as-Windows-1252 sequences ("smart quotes" and
// dashes that got double-encoded somewhere in a subtitle's history). Each
// entry is commented with the real UTF-8 byte sequence it corrects —
// verified by direct computation, not assumption, since em-dash and
// en-dash's mis-decoded forms differ ONLY in their third character
// (U+201D vs U+201C) and are easy to conflate. Previously both dash
// entries used a plain ASCII quote for that third character, making them
// byte-for-byte identical strings — the second (en-dash) was silently
// dead code, since .replace() calls are sequential and the first
// matching entry always wins.
private val encodingFixMap = listOf(
    "\u00e2\u20ac\u2122" to "'",          // E2 80 99 -> right single quote
    "\u00e2\u20ac\u2018" to "'",          // E2 80 98 -> left single quote
    "\u00e2\u20ac\u0153" to "\"",         // E2 80 9C -> left double quote
    "\u00e2\u20ac\u009D" to "\"",         // E2 80 9D -> right double quote, raw C1-passthrough variant
    "\u00e2\u20ac\u201D" to "\u2014",     // E2 80 94 -> em dash — FIXED, was identical to the en-dash entry below
    "\u00e2\u20ac\u201C" to "\u2013",     // E2 80 93 -> en dash — FIXED, was identical to the em-dash entry above
    "\u00c2 " to " ", "\u00c2\u00b0" to "\u00b0", "\u00c3\u00a9" to "\u00e9", "\u00c3\u00a8" to "\u00e8", "\u00c3 " to "\u00e0"
)

// FIX (tolerant parser round): real-world subtitle files aren't always
// textbook-clean SRT. Three specific gaps fixed here, each independently
// confirmed to silently break parsing before this fix:
//   1. A UTF-8 BOM (\uFEFF) at the very start of the file would corrupt
//      the FIRST block's index field (it'd read as "\uFEFF1" instead of
//      "1") — harmless for index itself since it's just a label, but a
//      BOM anywhere else after unusual re-saving could land right before
//      a timing line and break the "-->" check.
//   2. Only \r\n (Windows CRLF) was normalized — a file saved with old
//      Mac-style bare \r line endings would never split into blocks at
//      all, since the block-boundary regex only looks for \n.
//   3. A block missing its cue-NUMBER line (some malformed/hand-edited
//      SRTs go straight to the timing line) was silently DROPPED
//      entirely, losing that cue rather than recovering it.
internal fun parseSrtBlocks(text: String): List<SrtBlock> {
    val blocks = mutableListOf<SrtBlock>()
    val normalized = text
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val rawBlocks = normalized.split(Regex("\n\\s*\n"))
    for (raw in rawBlocks) {
        val lines = raw.trim().split("\n")
        if (lines.isEmpty()) continue

        // Missing cue-number recovery: if the FIRST line already looks
        // like a timing line, treat the block as having no explicit
        // index rather than rejecting it outright.
        if (lines[0].contains("-->")) {
            val timing = lines[0].trim()
            val textLines = lines.drop(1)
            blocks.add(SrtBlock(index = "", timing = timing, lines = textLines))
            continue
        }

        if (lines.size < 2) continue
        val index = lines[0].trim()
        val timing = lines.getOrNull(1)?.trim() ?: continue
        if (!timing.contains("-->")) continue
        val textLines = lines.drop(2)
        blocks.add(SrtBlock(index, timing, textLines))
    }
    return blocks
}

private fun cleanLine(line: String, options: SubtitleCleaningOptions): String {
    var result = line

    if (options.removeHtmlTags) result = htmlTagRegex.replace(result, "")

    if (options.correctEncodingSymbols) {
        encodingFixMap.forEach { (bad, good) -> result = result.replace(bad, good) }
    }

    if (options.removeSpeakerNames) result = speakerLabelRegex.replace(result, "")

    if (options.convertAllCaps) {
        val letters = result.filter { it.isLetter() }
        if (letters.length >= 4 && letters.all { it.isUpperCase() }) {
            result = result.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    return result.trim()
}

private fun cleanBlockLines(lines: List<String>, options: SubtitleCleaningOptions): List<String> {
    var working = lines

    if (options.hideHearingImpairedDescriptions) {
        working = working.filterNot { wholeLineBracketRegex.matches(it) || musicNoteRegex.matches(it) }
    }

    working = working.map { cleanLine(it, options) }.filter { it.isNotBlank() }

    if (options.removeDuplicateLines) {
        val deduped = mutableListOf<String>()
        for (line in working) {
            if (deduped.isEmpty() || deduped.last() != line) deduped.add(line)
        }
        working = deduped
    }

    if (options.mergeVeryShortLines) {
        // Merge lines under ~18 chars into the next line, so fragments like
        // "I know." / "But still..." split across two cue lines for no
        // reason become one — a common artifact of auto-generated subs.
        val merged = mutableListOf<String>()
        var buffer: String? = null
        for (line in working) {
            buffer = if (buffer == null) line
            else if (buffer.length < 18) "$buffer $line"
            else { merged.add(buffer); line }
        }
        buffer?.let { merged.add(it) }
        working = merged
    }

    if (options.fixBrokenLineBreaks && working.size > 2) {
        // More than 2 lines in one cue almost always means the original
        // line-wrapping broke (mid-word wraps, one-word-per-line dumps) —
        // rejoin everything and let the player's own wrapping handle it,
        // rather than showing 3-4 short choppy lines.
        working = listOf(working.joinToString(" "))
    }

    return working
}

fun cleanSrtText(original: String, options: SubtitleCleaningOptions): String {
    if (!options.isAnyEnabled) return original
    val blocks = parseSrtBlocks(original)
    if (blocks.isEmpty()) return original

    val output = StringBuilder()
    var newIndex = 1
    for (block in blocks) {
        val cleanedLines = cleanBlockLines(block.lines, options)
        if (cleanedLines.isEmpty()) continue // whole cue was e.g. just a sound cue — drop it entirely
        output.append(newIndex).append("\n")
        output.append(normalizeTimingLine(block.timing)).append("\n")
        cleanedLines.forEach { output.append(it).append("\n") }
        output.append("\n")
        newIndex++
    }
    return output.toString().trim()
}
