package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource

class MediaServerNetworkSource(
    override val displayName: String,
    private val kind: MediaServerKind,
    private val api: MediaServerApi,
    private val sessionProvider: () -> MediaServerSession?,
) : NetworkSource {
    override val type: NetworkType =
        if (kind == MediaServerKind.JELLYFIN) NetworkType.JELLYFIN else NetworkType.EMBY

    override val id: String = stableNetworkSourceId(type, api.baseUrl)

    override fun createDataSourceFactory(): DataSource.Factory {
        val session = sessionProvider()
        return DefaultHttpDataSource.Factory().apply {
            if (session != null) {
                setDefaultRequestProperties(
                    mapOf(
                        "X-Emby-Token" to session.accessToken,
                        "X-Emby-Authorization" to api.authorizationHeader(session),
                    )
                )
            }
        }
    }

    override suspend fun scan(): List<NetworkVideo> {
        val session = sessionProvider() ?: return emptyList()
        return api.getItems(session).map { item ->
            NetworkVideo(
                path = api.directStreamUrl(item, session),
                name = mediaServerItemTitle(item),
                size = item.size,
                posterUrl = api.imageUrl(item, session),
                overview = item.overview,
                rating = item.communityRating,
            )
        }
    }

    override suspend fun testConnection(): NetworkConnectionResult {
        val session = sessionProvider() ?: return safeNetworkFailure(displayName)
        return api.test(session)
    }
}

fun jellyfinNetworkSource(
    serverUrl: String,
    displayName: String,
    sessionProvider: () -> MediaServerSession?,
): MediaServerNetworkSource =
    MediaServerNetworkSource(
        displayName = displayName,
        kind = MediaServerKind.JELLYFIN,
        api = MediaServerApi(serverUrl, MediaServerKind.JELLYFIN),
        sessionProvider = sessionProvider,
    )

fun embyNetworkSource(
    serverUrl: String,
    displayName: String,
    sessionProvider: () -> MediaServerSession?,
): MediaServerNetworkSource =
    MediaServerNetworkSource(
        displayName = displayName,
        kind = MediaServerKind.EMBY,
        api = MediaServerApi(serverUrl, MediaServerKind.EMBY),
        sessionProvider = sessionProvider,
    )
