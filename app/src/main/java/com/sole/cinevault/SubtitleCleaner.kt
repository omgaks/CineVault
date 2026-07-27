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

// Common mis-decoded UTF-8-as-Latin-1 sequences ("smart quotes" and similar
// punctuation that got double-encoded somewhere in a subtitle's history).
private val encodingFixMap = listOf(
    "â€™" to "'", "â€˜" to "'", "â€œ" to "\"", "â€\u009D" to "\"", "â€\"" to "—",
    "â€\"" to "–", "Â " to " ", "Â°" to "°", "Ã©" to "é", "Ã¨" to "è", "Ã " to "à"
)

internal fun parseSrtBlocks(text: String): List<SrtBlock> {
    val blocks = mutableListOf<SrtBlock>()
    val rawBlocks = text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
    for (raw in rawBlocks) {
        val lines = raw.trim().split("\n")
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
        output.append(block.timing).append("\n")
        cleanedLines.forEach { output.append(it).append("\n") }
        output.append("\n")
        newIndex++
    }
    return output.toString().trim()
}
