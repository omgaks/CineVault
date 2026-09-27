package com.sole.cinevault.network

enum class MediaServerKind { JELLYFIN, EMBY }

data class MediaServerSession(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val serverId: String? = null,
)

data class MediaServerItem(
    val id: String,
    val name: String,
    val type: String?,
    val mediaType: String?,
    val container: String?,
    val overview: String?,
    val communityRating: Float?,
    val primaryImageTag: String?,
    val seriesName: String?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val size: Long = 0L,
)

internal fun normalizeServerUrl(value: String): String =
    value.trim().trimEnd('/')

internal fun mediaServerItemTitle(item: MediaServerItem): String =
    if (item.type.equals("Episode", true) && !item.seriesName.isNullOrBlank()) {
        buildString {
            append(item.seriesName)
            if (item.parentIndexNumber != null || item.indexNumber != null) {
                append(" - ")
                item.parentIndexNumber?.let { append("S").append(it.toString().padStart(2, '0')) }
                item.indexNumber?.let { append("E").append(it.toString().padStart(2, '0')) }
            }
            if (item.name.isNotBlank()) append(" - ").append(item.name)
        }
    } else item.name
