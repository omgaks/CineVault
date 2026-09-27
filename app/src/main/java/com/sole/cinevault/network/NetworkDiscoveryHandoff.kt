package com.sole.cinevault.network

import java.net.URI

sealed interface DiscoveredDeviceAction {
    data class OpenDlna(val source: DlnaNetworkSource) : DiscoveredDeviceAction
    data class PrefillManual(val type: NetworkType, val address: String) : DiscoveredDeviceAction
    data class CineVaultPeer(val device: DiscoveredNetworkDevice) : DiscoveredDeviceAction
    data class Unsupported(val reason: String) : DiscoveredDeviceAction
}

fun discoveredDeviceAction(device: DiscoveredNetworkDevice): DiscoveredDeviceAction {
    val address = device.addressHint?.trim().orEmpty()
    return when (device.kind) {
        NetworkDiscoveryKind.DLNA -> {
            if (!isHttpEndpoint(address)) {
                DiscoveredDeviceAction.Unsupported("This DLNA device did not provide a usable address.")
            } else {
                DiscoveredDeviceAction.OpenDlna(
                    DlnaNetworkSource(
                        device = DlnaDevice(
                            usn = device.id,
                            location = address,
                            server = device.displayName,
                        ),
                        displayName = device.displayName,
                    ),
                )
            }
        }

        NetworkDiscoveryKind.WEBDAV ->
            prefill(device, NetworkType.WEBDAV, address)

        NetworkDiscoveryKind.JELLYFIN ->
            prefill(device, NetworkType.JELLYFIN, address)

        NetworkDiscoveryKind.EMBY ->
            prefill(device, NetworkType.EMBY, address)

        NetworkDiscoveryKind.CINEVAULT ->
            if (!isHttpEndpoint(address)) {
                DiscoveredDeviceAction.Unsupported("This CineVault peer did not provide a usable LAN endpoint.")
            } else {
                DiscoveredDeviceAction.CineVaultPeer(device.copy(addressHint = normalizeHttpEndpoint(address)))
            }

        NetworkDiscoveryKind.SMB ->
            DiscoveredDeviceAction.Unsupported("Use your saved SMB shares from Network or Settings.")

        NetworkDiscoveryKind.UNKNOWN ->
            DiscoveredDeviceAction.Unsupported("CineVault cannot identify this network device yet.")
    }
}

private fun prefill(
    device: DiscoveredNetworkDevice,
    type: NetworkType,
    address: String,
): DiscoveredDeviceAction =
    if (!isHttpEndpoint(address)) {
        DiscoveredDeviceAction.Unsupported("${device.displayName} did not provide a usable address.")
    } else {
        DiscoveredDeviceAction.PrefillManual(type, normalizeHttpEndpoint(address))
    }

internal fun isHttpEndpoint(value: String): Boolean = runCatching {
    val uri = URI(value)
    (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo.isNullOrBlank()
}.getOrDefault(false)

internal fun normalizeHttpEndpoint(value: String): String =
    value.trim().removeSuffix("/")

private const val CINEVAULT_QR_PREFIX = "cinevault://nearby?"

data class CineVaultQrPayload(
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val nonce: String,
    val expiresAtEpochMs: Long,
)

fun parseCineVaultQrPayload(raw: String): CineVaultQrPayload? {
    val text = raw.trim()
    if (!text.startsWith(CINEVAULT_QR_PREFIX, ignoreCase = true)) return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val params = uri.rawQuery.orEmpty()
        .split('&')
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null
            else decode(pair.substring(0, index)) to decode(pair.substring(index + 1))
        }
        .toMap()

    val id = params["id"]?.takeIf(::isSafeLanDeviceId) ?: return null
    val name = params["name"]?.trim()?.takeIf(String::isNotBlank)?.take(80) ?: "CineVault"
    val endpoint = params["endpoint"]?.takeIf(::isHttpEndpoint)?.let(::normalizeHttpEndpoint) ?: return null
    val nonce = params["nonce"]?.takeIf { it.length in 16..128 && it.all(Char::isLetterOrDigit) } ?: return null
    val expires = params["expires"]?.toLongOrNull()?.takeIf { it > 0L } ?: return null

    return CineVaultQrPayload(id, name, endpoint, nonce, expires)
}

fun CineVaultQrPayload.asDiscoveredDevice(): DiscoveredNetworkDevice =
    DiscoveredNetworkDevice(
        id = deviceId,
        displayName = deviceName,
        kind = NetworkDiscoveryKind.CINEVAULT,
        addressHint = endpoint,
    )

private fun decode(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
