package com.sole.cinevault.smb

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.sole.cinevault.network.NetworkConnectionResult
import com.sole.cinevault.network.NetworkSource
import com.sole.cinevault.network.NetworkType
import com.sole.cinevault.network.NetworkVideo
import com.sole.cinevault.network.stableNetworkSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import jcifs.smb.SmbFile

/**
 * Adapter around CineVault's already-proven SMB implementation.
 *
 * D16 does not replace SMB scanning, credential storage or playback. This
 * exposes them through the common NetworkSource boundary so future protocols
 * can join the library without special-casing the player.
 */
@UnstableApi
class SmbNetworkSource(
    context: Context,
    private val share: SmbShare,
) : NetworkSource {

    private val appContext = context.applicationContext

    override val id: String = stableNetworkSourceId(
        type = NetworkType.SMB,
        host = share.host,
        resource = share.shareName + "/" + share.subPath.trim('/'),
    )

    override val displayName: String = share.displayName
    override val type: NetworkType = NetworkType.SMB

    override fun createDataSourceFactory(): DataSource.Factory =
        SmbDataSourceFactory(appContext)

    override suspend fun scan(): List<NetworkVideo> =
        when (val result = scanSmbShare(share)) {
            is SmbScanResult.Success -> result.videos.map { item ->
                NetworkVideo(
                    path = item.video.path,
                    name = item.video.name,
                    posterUrl = item.posterUrl,
                    overview = item.overview,
                    rating = item.rating,
                )
            }
            is SmbScanResult.Failure -> emptyList()
        }

    override suspend fun testConnection(): NetworkConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val root = SmbFile(share.rootUrl(), buildCifsContext(share))
                if (root.exists()) {
                    NetworkConnectionResult.Connected
                } else {
                    NetworkConnectionResult.Failed(
                        "Couldn't reach ${share.displayName}. Check the address and network."
                    )
                }
            } catch (_: Exception) {
                // Deliberately do not surface raw exception text: SMB/auth
                // libraries can include host/user details in their messages.
                NetworkConnectionResult.Failed(
                    "Couldn't connect to ${share.displayName}. Check the network and sign-in details."
                )
            }
        }
}
