package com.sole.cinevault.network

/**
 * Small parser seam for WebDAV PROPFIND responses.
 * Network I/O is added behind NetworkSource after this parser/storage slice.
 */
fun extractWebDavVideoHrefs(xml: String, baseUrl: String): List<NetworkVideo> {
    val hrefRegex = Regex("""(?is)<(?:[a-z0-9_-]+:)?href[^>]*>(.*?)</(?:[a-z0-9_-]+:)?href>""")
    return hrefRegex.findAll(xml).mapNotNull { match ->
        val encoded = decodeBasicXml(match.groupValues[1].trim())
        val url = resolveHttpUrl(baseUrl, encoded) ?: return@mapNotNull null
        val clean = url.substringBefore('?')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("mkv","mp4","m4v","avi","mov","webm","ts","m2ts","mts","mpg","mpeg"))
            return@mapNotNull null
        NetworkVideo(path = url, name = clean.substringAfterLast('/'))
    }.distinctBy { it.path }.toList()
}

private fun decodeBasicXml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
