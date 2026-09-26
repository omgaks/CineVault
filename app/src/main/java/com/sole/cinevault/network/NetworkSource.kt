package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import com.sole.cinevault.VideoWithMetadata

/**
 * Common boundary for every remote library CineVault can browse/play.
 *
 * Security rule: [id] is an opaque stable identifier only. It must never
 * contain usernames, passwords, access tokens, API keys or share URLs with
 * embedded credentials.
 */
interface NetworkSource {
    val id: String
    val displayName: String
    val type: NetworkType

    fun createDataSourceFactory(): DataSource.Factory
    suspend fun scan(): List<NetworkVideo>
    suspend fun testConnection(): NetworkConnectionResult
    suspend fun getMetadata(video: NetworkVideo): VideoWithMetadata? = null
}

data class NetworkVideo(
    val path: String,
    val name: String,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val posterUrl: String? = null,
    val overview: String? = null,
    val rating: Float? = null,
)

enum class NetworkType {
    SMB,
    WEBDAV,
    JELLYFIN,
    EMBY,
    DLNA,
    SFTP,
    HTTP_DIRECTORY,
    M3U,
    CINEVAULT_GATEWAY,
}

sealed interface NetworkConnectionResult {
    data object Connected : NetworkConnectionResult
    data class Failed(val userMessage: String) : NetworkConnectionResult
}
