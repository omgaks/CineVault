package com.sole.cinevault

import java.net.URLDecoder

/**
 * The plain file name at the end of a path or address.
 *
 * Normal paths just take what follows the last slash. Files from a picked folder or
 * "Open with" arrive as content:// addresses whose last part is an encoded document id
 * ("primary%3ADownload%2FSeal%2FMy%20Film.mp4"), so those are decoded first.
 */
internal fun fileLeafName(path: String): String {
    val isContent = path.startsWith("content://", ignoreCase = true)
    val text = if (isContent) {
        runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)
    } else path
    val leaf = text.substringAfterLast('/').substringAfterLast('\\')
    return if (isContent) leaf.substringAfterLast(':') else leaf
}
