package com.sole.cinevault.network

private val videoExtensions = setOf(
    "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mts", "mpg", "mpeg"
)

data class PlaylistEntry(val name: String, val url: String)

fun parseM3u(text: String): List<PlaylistEntry> {
    val result = mutableListOf<PlaylistEntry>()
    var pendingName: String? = null
    text.lineSequence().map(String::trim).forEach { line ->
        when {
            line.isBlank() -> Unit
            line.startsWith("#EXTINF:", ignoreCase = true) ->
                pendingName = line.substringAfter(',', "").trim().ifBlank { null }
            line.startsWith("#") -> Unit
            isSafeHttpUrl(line) -> {
                result += PlaylistEntry(
                    name = pendingName ?: line.substringAfterLast('/').substringBefore('?')
                        .ifBlank { "Stream" },
                    url = line,
                )
                pendingName = null
            }
            else -> pendingName = null
        }
    }
    return result
}

fun extractHttpDirectoryVideos(html: String, baseUrl: String): List<NetworkVideo> {
    val href = Regex("""(?i)href\s*=\s*["']([^"'#]+)["']""")
    return href.findAll(html).mapNotNull { match ->
        val raw = match.groupValues[1].trim()
        val absolute = resolveHttpUrl(baseUrl, raw) ?: return@mapNotNull null
        val cleanPath = absolute.substringBefore('?').substringBefore('#')
        val ext = cleanPath.substringAfterLast('.', "").lowercase()
        if (ext !in videoExtensions) return@mapNotNull null
        NetworkVideo(
            path = absolute,
            name = cleanPath.substringAfterLast('/').ifBlank { "Video" },
        )
    }.distinctBy { it.path }.toList()
}

fun isSafeHttpUrl(value: String): Boolean {
    val lower = value.trim().lowercase()
    return (lower.startsWith("https://") || lower.startsWith("http://")) &&
        !Regex("""(?i)^[a-z][a-z0-9+.-]*://[^/@\s]+@""").containsMatchIn(value)
}

internal fun resolveHttpUrl(baseUrl: String, href: String): String? {
    if (isSafeHttpUrl(href)) return href
    if (href.contains("://") || href.startsWith("//")) return null
    if (!isSafeHttpUrl(baseUrl)) return null
    val root = baseUrl.substringBeforeLast('/', baseUrl)
    val origin = Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
        .find(baseUrl)?.groupValues?.get(1) ?: return null
    return when {
        href.startsWith("/") -> origin + href
        else -> "$root/$href"
    }
}
