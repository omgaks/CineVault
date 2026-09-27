package com.sole.cinevault.network

data class DlnaDevice(
    val usn: String,
    val location: String,
    val server: String? = null,
)

data class DlnaService(
    val serviceType: String,
    val controlUrl: String,
)

data class DlnaMediaItem(
    val id: String,
    val parentId: String?,
    val title: String,
    val resourceUrl: String,
    val mimeType: String?,
    val size: Long = 0L,
    val duration: String? = null,
    val albumArtUrl: String? = null,
)

internal fun resolveDlnaUrl(base: String, value: String): String =
    java.net.URI(base).resolve(value.trim()).toString()

internal fun isDlnaMediaMime(value: String?): Boolean =
    value?.lowercase()?.let { it.startsWith("video/") || it.startsWith("audio/") } == true
