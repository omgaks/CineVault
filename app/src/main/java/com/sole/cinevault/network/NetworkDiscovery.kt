package com.sole.cinevault.network

enum class NetworkDiscoveryKind {
    CINEVAULT,
    JELLYFIN,
    EMBY,
    DLNA,
    WEBDAV,
    SMB,
    UNKNOWN,
}

data class DiscoveredNetworkDevice(
    val id: String,
    val displayName: String,
    val kind: NetworkDiscoveryKind,
    val addressHint: String? = null,
)

/**
 * Discovery boundary only. Implementations may use mDNS/SSDP/Nearby later.
 * Discovery never connects, authenticates, or exposes a library by itself.
 */
interface NetworkDiscoveryProvider {
    suspend fun discover(): List<DiscoveredNetworkDevice>
}

fun sanitizeDiscoveredDevices(
    devices: List<DiscoveredNetworkDevice>,
): List<DiscoveredNetworkDevice> =
    devices.asSequence()
        .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }
        .map {
            it.copy(
                displayName = it.displayName.trim().take(80),
                addressHint = it.addressHint
                    ?.let(::redactNetworkAddress)
                    ?.takeIf { hint -> !containsCredentialMaterial(hint) }
                    ?.take(120),
            )
        }
        .distinctBy { it.id }
        .toList()

internal fun containsCredentialMaterial(value: String): Boolean =
    Regex("""(?i)[a-z][a-z0-9+.-]*://[^/@\s]+@""").containsMatchIn(value)
