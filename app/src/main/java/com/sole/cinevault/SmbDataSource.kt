package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import java.io.IOException

// ── Why this file exists ────────────────────────────────────────────────
// Standard Media3 has no idea what an smb:// URI is — it isn't a local
// java.io.File and it isn't HTTP. SmbVideoScanner.kt already proved SMB
// connectivity works fine for scanning (via jcifs-ng's SmbFile), but that
// was only ever used to list files, never to actually stream one into
// ExoPlayer. SmbDataSource below is the missing piece: a real, seekable
// Media3 DataSource that reads bytes from an SmbFile the same way
// DefaultDataSource reads bytes from a local file or an HTTP response.

// Matches a playing smb:// path back to the saved SmbShare that has its
// credentials — deliberately by host+shareName rather than a full prefix
// match on rootUrl(), since rootUrl() embeds the optional subPath and the
// URL actually being played is a full file path nested under it; matching
// on just the first two path segments is simpler and equally unambiguous
// (CineVault doesn't support two saved shares with the same host+shareName).
private fun findMatchingShare(context: Context, urlString: String): SmbShare? {
    val withoutScheme = urlString.removePrefix("smb://")
    val segments = withoutScheme.split("/")
    if (segments.size < 2) return null
    val host = segments[0]
    val shareName = segments[1]
    return loadSmbShares(context).firstOrNull {
        it.host.equals(host, ignoreCase = true) && it.shareName.equals(shareName, ignoreCase = true)
    }
}

// SmbFileInputStream.skip() performs a real positional seek over SMB rather
// than reading-and-discarding bytes (SMB reads are offset-based, not purely
// sequential) — but skip() on any InputStream is technically allowed to
// return less than requested in one call, so this loops until the full
// amount is actually skipped, exactly like DefaultDataSource does for local
// files.
@Throws(IOException::class)
private fun skipFully(stream: SmbFileInputStream, bytesToSkip: Long) {
    var remaining = bytesToSkip
    while (remaining > 0) {
        val skipped = stream.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            // skip() stalled — fall back to a single discarded read rather
            // than spinning forever.
            if (stream.read() == -1) {
                throw IOException("Reached end of file while seeking to the requested position")
            }
            remaining -= 1
        }
    }
}

@UnstableApi
class SmbDataSource(private val appContext: Context) : BaseDataSource(/* isNetwork = */ true) {

    private var inputStream: SmbFileInputStream? = null
    private var openedUri: Uri? = null
    private var bytesRemaining: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        val urlString = dataSpec.uri.toString()
        val share = findMatchingShare(appContext, urlString)
            ?: throw IOException("No saved network share matches this file (was it removed from Settings? \"$urlString\")")

        val cifsContext = buildCifsContext(share)
        val file = SmbFile(urlString, cifsContext)

        val stream = try {
            SmbFileInputStream(file)
        } catch (e: Exception) {
            throw IOException("Couldn't open network file: ${e.message}", e)
        }

        if (dataSpec.position > 0) {
            try {
                skipFully(stream, dataSpec.position)
            } catch (e: IOException) {
                try { stream.close() } catch (_: Exception) {}
                throw IOException("Couldn't seek to the requested position in this network file", e)
            }
        }

        val fileLength = try { file.length() } catch (_: Exception) { C.LENGTH_UNSET.toLong() }
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            fileLength >= 0L -> fileLength - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        if (bytesRemaining < 0L && bytesRemaining != C.LENGTH_UNSET.toLong()) {
            try { stream.close() } catch (_: Exception) {}
            throw IOException("Requested read position is beyond the end of this network file")
        }

        inputStream = stream
        openedUri = dataSpec.uri
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream ?: throw IOException("SMB stream isn't open")
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()

        val read = try {
            stream.read(buffer, offset, bytesToRead)
        } catch (e: Exception) {
            throw IOException("Network read failed: ${e.message}", e)
        }

        if (read == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining != 0L) {
                // The share reported more bytes than it actually delivered —
                // treat as an error rather than silently truncating playback.
                throw IOException("Network file ended earlier than expected")
            }
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {
            // Nothing useful to do with a close failure on a stream we're
            // discarding anyway.
        } finally {
            inputStream = null
            openedUri = null
            transferEnded()
        }
    }
}

@UnstableApi
class SmbDataSourceFactory(private val appContext: Context) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(appContext)
}

// Router DataSource — CineVault plays local files, direct http(s) stream
// links, AND now smb:// network files through the same ExoPlayer instance.
// This picks the right underlying DataSource per media item based on the
// URI scheme, so local/stream playback keeps using Media3's own
// DefaultDataSource exactly as before, and only smb:// paths get routed to
// SmbDataSource above.
@UnstableApi
class CineVaultDataSource(private val appContext: Context) : DataSource {
    private val defaultDataSource: DataSource by lazy { DefaultDataSource.Factory(appContext).createDataSource() }
    private val smbDataSource: DataSource by lazy { SmbDataSource(appContext) }
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        defaultDataSource.addTransferListener(transferListener)
        smbDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val chosen = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) smbDataSource else defaultDataSource
        active = chosen
        return chosen.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return active?.read(buffer, offset, length) ?: throw IOException("DataSource not opened")
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        active?.close()
    }
}

@UnstableApi
class CineVaultDataSourceFactory(private val appContext: Context) : DataSource.Factory {
    override fun createDataSource(): DataSource = CineVaultDataSource(appContext)
}

// Ready-to-use MediaSourceFactory for ExoPlayer.Builder().setMediaSourceFactory(...) —
// wraps CineVaultDataSourceFactory so VideoPlayerScreen.kt doesn't need to
// know any of the routing details above, just this one call.
@UnstableApi
fun cineVaultMediaSourceFactory(context: Context): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(context).setDataSourceFactory(CineVaultDataSourceFactory(context.applicationContext))
