package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri

// FIX: these two functions used to live inside VideoPlayerScreen.kt
// itself (marked internal so AutoSyncCoordinator.kt and
// SubtitleSyncToolsCoordinator.kt, in a different package, could reach
// them). That created a real, recurring problem — VideoPlayerScreen.kt
// is a huge file that changes on every single slice of this extraction
// effort, and several pushes in a row landed with these two files
// slightly out of sync with each other, breaking the build each time
// despite the actual code being correct on its own. Moving them here —
// a small, standalone file with no other reason to change — removes
// that dependency on VideoPlayerScreen.kt's state entirely. Both
// coordinators are in the same package as this file, so no import is
// needed on their end at all.

fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

fun computeDriftTransform(pointA: DriftPoint, pointB: DriftPoint): Pair<Float, Long> {
    val t1 = pointA.positionMs.toDouble()
    val t2 = pointB.positionMs.toDouble()
    val c1 = t1 + pointA.correctionSeconds * 1000.0
    val c2 = t2 + pointB.correctionSeconds * 1000.0
    if (t2 == t1) return 1f to 0L
    val scale = (c2 - c1) / (t2 - t1)
    val shift = c1 - scale * t1
    return scale.toFloat() to shift.toLong()
}

// Added proactively (not after another failed build this time) — needed
// by the subtitle-search extraction, and was heading toward the exact
// same "private function still in VideoPlayerScreen.kt, needed from a
// different file" shape that caused real recurring problems earlier
// tonight. Belongs here for the same reason readTextFromUri/
// computeDriftTransform do.
fun buildCleanedSubtitleFile(context: Context, sourceUri: Uri, options: SubtitleCleaningOptions): Uri? {
    if (!options.isAnyEnabled) return sourceUri
    if (!supportsCustomTextPipeline(detectSubtitleFormat(sourceUri))) return sourceUri
    val original = readTextFromUri(context, sourceUri) ?: return null
    val cleaned = cleanSrtText(original, options)
    return try {
        // Unique per source file + the exact cleaning options applied —
        // avoids a shared fixed filename racing between overlapping
        // requests.
        val uniqueName = "cinevault_cleaned_${sourceUri.hashCode()}_${options.hashCode()}.srt"
        val outFile = java.io.File(context.cacheDir, uniqueName)
        outFile.writeText(cleaned)
        Uri.fromFile(outFile)
    } catch (e: Exception) {
        null
    }
}
