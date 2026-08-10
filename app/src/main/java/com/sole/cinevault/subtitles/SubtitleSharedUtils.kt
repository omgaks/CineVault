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
