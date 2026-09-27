package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient

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
            client.addHostKeyVerifier(endpoint.hostKeySha256)
            client.connect(endpoint.host, endpoint.port)
            client.authPassword(credential.username, credential.secret)
            block(client)
        }
    }
}
