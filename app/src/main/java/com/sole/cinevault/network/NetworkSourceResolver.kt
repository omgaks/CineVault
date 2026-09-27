package com.sole.cinevault.network

import android.content.Context
import java.net.URI

class NetworkSourceResolver(context: Context) {
    private val credentials = NetworkCredentialStore(context.applicationContext)

    fun resolve(saved: SavedNetworkSource): NetworkSource {
        val credentialProvider = { credentials.load(saved.id) }
        return when (saved.type) {
            NetworkType.WEBDAV -> createManualNetworkSource(
                ManualNetworkSourceSpec.WebDav(saved.displayName, saved.address, credentialProvider)
            )
            NetworkType.HTTP_DIRECTORY -> createManualNetworkSource(
                ManualNetworkSourceSpec.HttpDirectory(saved.displayName, saved.address, credentialProvider)
            )
            NetworkType.M3U -> createManualNetworkSource(
                ManualNetworkSourceSpec.M3u(saved.displayName, saved.address, credentialProvider)
            )
            NetworkType.SFTP -> {
                val uri = URI(saved.address)
                createManualNetworkSource(
                    ManualNetworkSourceSpec.Sftp(
                        displayName = saved.displayName,
                        endpoint = SftpEndpoint(
                            host = uri.host ?: saved.address.removePrefix("sftp://").substringBefore('/').substringBefore(':'),
                            port = saved.port ?: if (uri.port > 0) uri.port else 22,
                            rootPath = saved.rootPath.ifBlank { uri.path.ifBlank { "/" } },
                            hostKeySha256 = saved.hostKeySha256,
                        ),
                        credentialProvider = credentialProvider,
                    )
                )
            }
            else -> error("${saved.type} is not a manual source type")
        }
    }
}

internal fun suggestedSourceName(type: NetworkType, address: String): String {
    val host = runCatching { URI(address).host }.getOrNull()
        ?: address.substringAfter("://", address).substringBefore('/').substringBefore(':')
    return when (type) {
        NetworkType.WEBDAV -> "WebDAV • $host"
        NetworkType.SFTP -> "SFTP • $host"
        NetworkType.HTTP_DIRECTORY -> "Web • $host"
        NetworkType.M3U -> "Playlist • $host"
        else -> host.ifBlank { type.name }
    }
}

internal fun normalizedManualAddress(type: NetworkType, raw: String): String {
    val value = raw.trim()
    require(value.isNotBlank()) { "Address is required" }
    require(!containsCredentialMaterial(value)) { "Put credentials in the secure sign-in fields" }
    return when (type) {
        NetworkType.WEBDAV, NetworkType.HTTP_DIRECTORY, NetworkType.M3U ->
            if ("://" in value) value else "http://$value"
        NetworkType.SFTP -> if (value.startsWith("sftp://")) value else "sftp://$value"
        else -> value
    }
}
