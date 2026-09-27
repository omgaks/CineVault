package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey
import java.util.Base64

class SftpNetworkSource(
    private val endpoint: SftpEndpoint,
    override val displayName: String,
    private val credentialProvider: () -> NetworkCredential?,
) : NetworkSource {
    override val type: NetworkType = NetworkType.SFTP
    override val id: String = stableNetworkSourceId(type, endpoint.redactedAddress)

    override fun createDataSourceFactory(): DataSource.Factory =
        SftpDataSource.Factory(endpoint, credentialProvider)

    override suspend fun scan(): List<NetworkVideo> = withContext(Dispatchers.IO) {
        runCatching {
            withSftp { client ->
                val sftp = client.newSFTPClient()
                try {
                    sftp.ls(normalizeSftpPath(endpoint.rootPath))
                        .asSequence()
                        .filterNot { it.isDirectory }
                        .filter { isNetworkVideoName(it.name) }
                        .map {
                            val path = normalizeSftpPath("${endpoint.rootPath}/${it.name}")
                            NetworkVideo(
                                path = "sftp://${endpoint.host}:${endpoint.port}$path",
                                name = it.name,
                                size = it.attributes.size,
                            )
                        }
                        .toList()
                } finally {
                    sftp.close()
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun testConnection(): NetworkConnectionResult = withContext(Dispatchers.IO) {
        try {
            withSftp { true }
            NetworkConnectionResult.Connected
        } catch (_: Exception) {
            safeNetworkFailure(displayName)
        }
    }

    internal fun <T> withSftp(block: (SSHClient) -> T): T {
        val credential = credentialProvider() ?: error("SFTP credentials unavailable")
        require(credential.username.isNotBlank()) { "SFTP username required" }

        return SSHClient().use { client ->
            client.addHostKeyVerifier(PinnedSha256HostKeyVerifier(endpoint.hostKeySha256))
            client.connect(endpoint.host, endpoint.port)
            client.authPassword(credential.username, credential.secret)
            block(client)
        }
    }
}

internal class PinnedSha256HostKeyVerifier(
    private val expected: String,
) : HostKeyVerifier {
    override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean =
        sshSha256Fingerprint(key) == expected
}

internal fun sshSha256Fingerprint(key: PublicKey): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.encoded)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}
