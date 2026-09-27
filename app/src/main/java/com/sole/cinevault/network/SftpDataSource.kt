package com.sole.cinevault.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteFile
import java.io.EOFException

class SftpDataSource(
    private val endpoint: SftpEndpoint,
    private val credentialProvider: () -> NetworkCredential?,
) : BaseDataSource(false) {
    private var client: SSHClient? = null
    private var remote: RemoteFile? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val requestedUri = dataSpec.uri
        require(requestedUri.scheme.equals("sftp", true))
        require(requestedUri.host == endpoint.host)
        require((if (requestedUri.port == -1) 22 else requestedUri.port) == endpoint.port)

        val path = normalizeSftpPath(requestedUri.path ?: "/")
        val credential = credentialProvider() ?: error("SFTP credentials unavailable")
        require(credential.username.isNotBlank())

        val ssh = SSHClient()
        try {
            ssh.addHostKeyVerifier(endpoint.hostKeySha256)
            ssh.connect(endpoint.host, endpoint.port)
            ssh.authPassword(credential.username, credential.secret)
            val file = ssh.newSFTPClient().open(path)
            val fileSize = file.fetchAttributes().size
            if (dataSpec.position > fileSize) throw EOFException()

            uri = requestedUri
            client = ssh
            remote = file
            position = dataSpec.position
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                fileSize - dataSpec.position
            } else {
                minOf(dataSpec.length, fileSize - dataSpec.position)
            }
            opened = true
            transferStarted(dataSpec)
            return remaining
        } catch (t: Throwable) {
            runCatching { ssh.close() }
            throw t
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val wanted = minOf(length.toLong(), remaining).toInt()
        val read = remote?.read(position, buffer, offset, wanted) ?: C.RESULT_END_OF_INPUT
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { remote?.close() }
        remote = null
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        client = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val endpoint: SftpEndpoint,
        private val credentialProvider: () -> NetworkCredential?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SftpDataSource(endpoint, credentialProvider)
    }
}
