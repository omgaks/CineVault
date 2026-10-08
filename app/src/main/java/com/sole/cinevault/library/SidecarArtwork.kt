package com.sole.cinevault.library

/**
 * Pure rules for finding artwork and NFO files that sit next to a video
 * (Kodi / Jellyfin / Plex naming). No Android calls in here, so it is testable.
 */
object SidecarNames {
    val imageExtensions = listOf("jpg", "jpeg", "png", "webp")

    private fun base(videoName: String) = videoName.substringBeforeLast('.')

    /** Names (without extension) to try, best first. Folder-wide names only when [allowGeneric]. */
    fun posterNames(videoName: String, allowGeneric: Boolean): List<String> {
        val b = base(videoName)
        val specific = listOf("$b-poster", "$b.poster", b)
        return if (allowGeneric) specific + listOf("poster", "folder", "cover", "movie") else specific
    }

    fun backdropNames(videoName: String, allowGeneric: Boolean): List<String> {
        val b = base(videoName)
        val specific = listOf("$b-fanart", "$b-backdrop", "$b-background", "$b.fanart")
        return if (allowGeneric) specific + listOf("fanart", "backdrop", "background") else specific
    }

    fun nfoNames(videoName: String, allowGeneric: Boolean): List<String> {
        val b = base(videoName)
        return if (allowGeneric) listOf(b, "movie") else listOf(b)
    }

    /** First file in [files] matching a wanted name + any of [extensions], case-insensitive. */
    fun pick(files: List<String>, wantedNames: List<String>, extensions: List<String> = imageExtensions): String? {
        val byLower = files.associateBy { it.lowercase() }
        for (name in wantedNames) for (ext in extensions) {
            byLower["${name.lowercase()}.$ext"]?.let { return it }
        }
        return null
    }
}

data class SafLocation(val volume: String, val dir: String, val fileName: String) {
    val parentDir: String? get() = if (dir.isEmpty()) null else dir.substringBeforeLast('/', "")
}

/** Maps a real filesystem path to its Storage Access Framework volume + folder. Null for URIs/streams. */
fun safLocationOf(videoPath: String): SafLocation? {
    val path = videoPath.trim()
    val rest: String
    val volume: String
    when {
        path.startsWith("/storage/emulated/0/") -> { volume = "primary"; rest = path.removePrefix("/storage/emulated/0/") }
        path.startsWith("/storage/self/primary/") -> { volume = "primary"; rest = path.removePrefix("/storage/self/primary/") }
        path.startsWith("/sdcard/") -> { volume = "primary"; rest = path.removePrefix("/sdcard/") }
        path.startsWith("/storage/") -> {
            val after = path.removePrefix("/storage/")
            val id = after.substringBefore('/')
            if (id.isBlank() || id == "emulated" || id == "self" || !after.contains('/')) return null
            volume = id; rest = after.substringAfter('/')
        }
        else -> return null
    }
    if (rest.isBlank()) return null
    val dir = rest.substringBeforeLast('/', "")
    return SafLocation(volume, dir, rest.substringAfterLast('/'))
}

fun safDocumentId(volume: String, dir: String): String = "$volume:$dir"

/** [treeDocId] is a picked folder's document id, e.g. "primary:Movies". */
fun treeCovers(treeDocId: String, volume: String, dir: String): Boolean {
    if (treeDocId.substringBefore(':') != volume) return false
    val rel = treeDocId.substringAfter(':', "").trim('/')
    return rel.isEmpty() || dir == rel || dir.startsWith("$rel/")
}

/** Kodi ids: <tmdbid>, <uniqueid type="tmdb">, or a bare themoviedb.org/movie/ link. */
fun parseNfoTmdbId(text: String): Int? {
    val patterns = listOf(
        Regex("<tmdbid>\\s*(\\d{1,9})\\s*</tmdbid>", RegexOption.IGNORE_CASE),
        Regex("<uniqueid[^>]*type=[\"']tmdb[\"'][^>]*>\\s*(\\d{1,9})\\s*</uniqueid>", RegexOption.IGNORE_CASE),
        Regex("themoviedb\\.org/movie/(\\d{1,9})", RegexOption.IGNORE_CASE),
    )
    for (p in patterns) p.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    return null
}
