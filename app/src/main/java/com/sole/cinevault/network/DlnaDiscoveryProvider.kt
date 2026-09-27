package com.sole.cinevault.network

class DlnaDiscoveryProvider(
    private val ssdp: SsdpDiscovery = SsdpDiscovery(),
) : NetworkDiscoveryProvider {
    override suspend fun discover(): List<DiscoveredNetworkDevice> =
        ssdp.discoverMediaServers().map { device ->
            DiscoveredNetworkDevice(
                id = stableNetworkSourceId(NetworkType.DLNA, device.location),
                displayName = dlnaDisplayName(device),
                kind = NetworkDiscoveryKind.DLNA,
                addressHint = device.location,
            )
        }
}

internal fun dlnaDisplayName(device: DlnaDevice): String =
    device.server
        ?.substringBefore(" UPnP", missingDelimiterValue = device.server)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(80)
        ?: "DLNA Media Server"
