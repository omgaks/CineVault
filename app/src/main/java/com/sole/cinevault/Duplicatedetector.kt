package com.sole.cinevault

import java.io.File

// ── Duplicate detection ──────────────────────────────────────────────────
// Detects the "same movie downloaded twice into different folders"
// scenario by comparing real on-disk FILE SIZE, not filename or title.
// Two different downloads of the same release are almost always byte-
// identical (or within a small tolerance for container/metadata
// differences), while filenames vary wildly across download sources/apps,
// and titles can coincidentally collide for genuinely different videos
// (remakes, sequels sharing a name, unrelated files with the same clean
// title). Runs entirely off File.length() — a filesystem metadata read,
// not a content read — so it stays fast even across a large library,
// unlike a full content hash which would mean reading every byte of every
// video file on every scan.
//
// Deliberately conservative: this only ever GROUPS candidates for the
// person to review — it never deletes anything itself. See
// LocalVideoLibraryScreen.kt's "Duplicates" category, which reuses the
// exact same consent-based delete flow (deleteVideoFile) already used
// everywhere else in the app.
data class DuplicateGroup(val videos: List<VideoWithMetadata>)

// Same-file copies should be byte-identical; this tolerance exists only
// for edge cases like a re-mux by a different app adding/stripping a few
// KB of container metadata around otherwise-identical video data — not
// loose enough to false-positive on two genuinely different videos that
// happen to be similarly sized.
private const val SIZE_TOLERANCE_BYTES = 512L * 1024L // 512KB

fun findDuplicateGroups(videos: List<VideoWithMetadata>): List<DuplicateGroup> {
    if (videos.size < 2) return emptyList()

    // SMB and content:// entries don't have a real local File to measure
    // cheaply/reliably here, and a network share vs. a local copy aren't
    // "the same file" for delete purposes even if they show the same
    // movie — duplicate detection is a local-storage cleanup concept.
    val sized = videos.mapNotNull { v ->
        val path = v.video.path
        if (path.startsWith("smb://", ignoreCase = true) || path.startsWith("content://", ignoreCase = true)) return@mapNotNull null
        val file = File(path)
        if (!file.exists()) return@mapNotNull null
        val size = file.length()
        if (size <= 0L) return@mapNotNull null
        v to size
    }
    if (sized.size < 2) return emptyList()

    // Sort by size so candidates within tolerance land next to each other,
    // then sweep once — O(n log n) instead of comparing every pair against
    // every other pair, which matters once a library has a few thousand
    // videos in it.
    val sortedBySize = sized.sortedBy { it.second }
    val groups = mutableListOf<MutableList<Pair<VideoWithMetadata, Long>>>()
    var currentGroup = mutableListOf<Pair<VideoWithMetadata, Long>>()

    for (entry in sortedBySize) {
        // Compared against the GROUP'S FIRST (smallest) member, not the
        // previous entry — anchoring to a fixed reference point avoids a
        // "chain" of small tolerance-steps drifting the group's overall
        // size range wider than SIZE_TOLERANCE_BYTES actually allows.
        if (currentGroup.isEmpty() || entry.second - currentGroup.first().second <= SIZE_TOLERANCE_BYTES) {
            currentGroup.add(entry)
        } else {
            if (currentGroup.size > 1) groups.add(currentGroup)
            currentGroup = mutableListOf(entry)
        }
    }
    if (currentGroup.size > 1) groups.add(currentGroup)

    return groups
        .map { group -> group.map { it.first }.distinctBy { it.video.path } }
        .filter { it.size > 1 }
        .map { DuplicateGroup(it) }
}

// Human-readable size for the duplicate-cleanup UI (e.g. "1.4 GB").
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "${value.toInt()} ${units[unitIndex]}" else "%.1f %s".format(value, units[unitIndex])
}
