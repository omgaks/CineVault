package com.sole.cinevault.network

internal fun mediaServerType(kind: MediaServerKind): NetworkType =
    if (kind == MediaServerKind.JELLYFIN) NetworkType.JELLYFIN else NetworkType.EMBY

internal fun mediaServerCredential(session: MediaServerSession): NetworkCredential =
    NetworkCredential(
        username = session.userId,
        secret = session.accessToken,
    )

internal fun mediaServerSession(
    source: SavedNetworkSource,
    credential: NetworkCredential?,
): MediaServerSession? {
    if (credential == null || credential.username.isBlank() || credential.secret.isBlank()) return null
    if (source.type != NetworkType.JELLYFIN && source.type != NetworkType.EMBY) return null
    return MediaServerSession(
        serverUrl = source.address,
        userId = credential.username,
        accessToken = credential.secret,
    )
}
