package com.sole.cinevault.network

import org.json.JSONArray
import org.json.JSONObject

internal fun parseMediaServerAuthentication(json: String, serverUrl: String): MediaServerSession? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val token = root.optString("AccessToken").takeIf(String::isNotBlank) ?: return null
    val user = root.optJSONObject("User") ?: return null
    val userId = user.optString("Id").takeIf(String::isNotBlank) ?: return null
    return MediaServerSession(
        serverUrl = normalizeServerUrl(serverUrl),
        userId = userId,
        accessToken = token,
        serverId = root.optString("ServerId").takeIf(String::isNotBlank),
    )
}

internal fun parseMediaServerItems(json: String): List<MediaServerItem> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val array = root.optJSONArray("Items") ?: JSONArray()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("Id").takeIf(String::isNotBlank) ?: continue
            val name = item.optString("Name").takeIf(String::isNotBlank) ?: continue
            add(
                MediaServerItem(
                    id = id,
                    name = name,
                    type = item.optString("Type").takeIf(String::isNotBlank),
                    mediaType = item.optString("MediaType").takeIf(String::isNotBlank),
                    container = item.optString("Container").takeIf(String::isNotBlank),
                    overview = item.optString("Overview").takeIf(String::isNotBlank),
                    communityRating = if (item.has("CommunityRating")) item.optDouble("CommunityRating").toFloat() else null,
                    primaryImageTag = item.optString("PrimaryImageTag").takeIf(String::isNotBlank),
                    seriesName = item.optString("SeriesName").takeIf(String::isNotBlank),
                    indexNumber = if (item.has("IndexNumber")) item.optInt("IndexNumber") else null,
                    parentIndexNumber = if (item.has("ParentIndexNumber")) item.optInt("ParentIndexNumber") else null,
                    size = item.optLong("Size", 0L),
                )
            )
        }
    }
}
