package com.sole.cinevault.network

import android.content.Context

/**
 * CONNECT3-5
 *
 * Persists only safe reconnect metadata.
 *
 * Deliberately NOT persisted:
 * - session/access token
 * - approval nonce
 * - pending approval
 * - expiry-bearing credentials
 *
 * A remembered device is therefore a reconnect hint, never a trusted session.
 * Reconnection still goes through the normal pairing/approval path.
 */
data class RememberedCineVaultPeer(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val rememberedAtEpochMs: Long,
) {
    fun asDiscoveredDevice(): DiscoveredNetworkDevice =
        DiscoveredNetworkDevice(
            id = id,
            displayName = displayName,
            kind = NetworkDiscoveryKind.CINEVAULT,
            addressHint = endpoint,
        )
}

class CineVaultReconnectStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): RememberedCineVaultPeer? {
        val id = prefs.getString(KEY_ID, null)?.trim().orEmpty()
        val name = prefs.getString(KEY_NAME, null)?.trim().orEmpty()
        val endpoint = prefs.getString(KEY_ENDPOINT, null)?.trim().orEmpty()
        val rememberedAt = prefs.getLong(KEY_REMEMBERED_AT, 0L)

        if (id.isBlank() || name.isBlank() || endpoint.isBlank()) return null
        if (!isSafeLanDeviceId(id)) {
            clear()
            return null
        }

        val sanitized = sanitizeDiscoveredDevices(
            listOf(
                DiscoveredNetworkDevice(
                    id = id,
                    displayName = name,
                    kind = NetworkDiscoveryKind.CINEVAULT,
                    addressHint = endpoint,
                )
            )
        ).singleOrNull()

        if (sanitized == null || sanitized.addressHint.isNullOrBlank()) {
            clear()
            return null
        }

        return RememberedCineVaultPeer(
            id = sanitized.id,
            displayName = sanitized.displayName,
            endpoint = sanitized.addressHint,
            rememberedAtEpochMs = rememberedAt,
        )
    }

    fun remember(connection: CineVaultRemoteConnection) {
        val device = sanitizeDiscoveredDevices(
            listOf(
                connection.device.copy(
                    kind = NetworkDiscoveryKind.CINEVAULT,
                    addressHint = connection.endpoint,
                )
            )
        ).singleOrNull() ?: return

        val endpoint = device.addressHint ?: return

        prefs.edit()
            .putString(KEY_ID, device.id)
            .putString(KEY_NAME, device.displayName)
            .putString(KEY_ENDPOINT, endpoint)
            .putLong(KEY_REMEMBERED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ID)
            .remove(KEY_NAME)
            .remove(KEY_ENDPOINT)
            .remove(KEY_REMEMBERED_AT)
            .apply()
    }

    companion object {
        private const val PREFS = "cinevault_connect_reconnect"
        private const val KEY_ID = "peer_id"
        private const val KEY_NAME = "peer_name"
        private const val KEY_ENDPOINT = "peer_endpoint"
        private const val KEY_REMEMBERED_AT = "remembered_at"
    }
}
