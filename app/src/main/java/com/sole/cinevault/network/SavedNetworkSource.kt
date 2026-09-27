package com.sole.cinevault.network

import org.json.JSONObject

/**
 * Non-secret network configuration. Passwords/tokens are NEVER stored here.
 * Secrets belong only in NetworkCredentialStore.
 */
data class SavedNetworkSource(
    val id: String,
    val displayName: String,
    val type: NetworkType,
    val address: String,
    val rootPath: String = "",
    val port: Int? = null,
    val hostKeySha256: String = "",
) {
    init {
        require(isSafeNetworkSourceId(id)) { "Unsafe network source id" }
        require(displayName.isNotBlank())
        require(address.isNotBlank())
        require(!containsCredentialMaterial(address)) { "Credentials must not be stored in source address" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", displayName.trim())
        .put("type", type.name)
        .put("address", address.trim())
        .put("rootPath", rootPath)
        .apply {
            port?.let { put("port", it) }
            if (hostKeySha256.isNotBlank()) put("hostKeySha256", hostKeySha256)
        }

    companion object {
        fun fromJson(json: JSONObject): SavedNetworkSource? = runCatching {
            SavedNetworkSource(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                type = NetworkType.valueOf(json.getString("type")),
                address = json.getString("address"),
                rootPath = json.optString("rootPath"),
                port = if (json.has("port")) json.getInt("port") else null,
                hostKeySha256 = json.optString("hostKeySha256"),
            )
        }.getOrNull()
    }
}
