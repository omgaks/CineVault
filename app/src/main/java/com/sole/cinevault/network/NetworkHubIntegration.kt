package com.sole.cinevault.network

import android.content.Context
import com.sole.cinevault.smb.SmbNetworkSource
import com.sole.cinevault.smb.loadSmbShares

/**
 * Single integration boundary used by the Network Hub.
 * Keeps persistence, encrypted credentials and source construction out of UI.
 */
class NetworkHubIntegration(context: Context) {
    private val appContext = context.applicationContext
    private val savedStore = SavedNetworkSourceStore(appContext)
    private val credentialStore = NetworkCredentialStore(appContext)
    private val resolver = NetworkSourceResolver(appContext)

    fun summaries(): List<NetworkHubSourceSummary> {
        val smb = loadSmbShares(appContext).map { share ->
            NetworkHubSourceSummary(
                id = "smb:${share.id}",
                name = share.displayName,
                typeLabel = "SMB",
                statusLabel = "Ready",
            )
        }
        val manual = savedStore.list().map { saved ->
            NetworkHubSourceSummary(
                id = saved.id,
                name = saved.displayName,
                typeLabel = saved.type.friendlyType(),
                statusLabel = "Ready",
            )
        }
        return (smb + manual).distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    fun save(source: SavedNetworkSource, credential: NetworkCredential?) {
        savedStore.save(source)
        if (credential != null) credentialStore.save(source.id, credential)
        else credentialStore.remove(source.id)
    }

    fun remove(sourceId: String) {
        if (sourceId.startsWith("smb:")) return // SMB remains owned by the existing SMB store.
        savedStore.remove(sourceId)
        credentialStore.remove(sourceId)
    }

    fun resolve(sourceId: String): NetworkSource? {
        if (sourceId.startsWith("smb:")) {
            val shareId = sourceId.removePrefix("smb:")
            return loadSmbShares(appContext).firstOrNull { it.id == shareId }
                ?.let { SmbNetworkSource(appContext, it) }
        }
        return savedStore.list().firstOrNull { it.id == sourceId }?.let(resolver::resolve)
    }
}

internal fun suggestedTypeForDiscovery(kind: NetworkDiscoveryKind): NetworkType? = when (kind) {
    NetworkDiscoveryKind.WEBDAV -> NetworkType.WEBDAV
    NetworkDiscoveryKind.SMB -> NetworkType.SMB
    NetworkDiscoveryKind.JELLYFIN -> NetworkType.JELLYFIN
    NetworkDiscoveryKind.EMBY -> NetworkType.EMBY
    NetworkDiscoveryKind.DLNA -> NetworkType.DLNA
    NetworkDiscoveryKind.CINEVAULT -> NetworkType.CINEVAULT_GATEWAY
    NetworkDiscoveryKind.UNKNOWN -> null
}

internal fun discoveryCanPrefillManualSetup(kind: NetworkDiscoveryKind): Boolean =
    kind == NetworkDiscoveryKind.WEBDAV
