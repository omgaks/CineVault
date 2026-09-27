package com.sole.cinevault.network

import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.library.VideoFile

/**
 * Converts a remote scan result into CineVault's existing player/navigation model.
 * The path remains the protocol engine's playable URI; no credentials are embedded.
 */
fun NetworkVideo.toPlayerMetadata(source: NetworkSource): VideoWithMetadata {
    val folder = path.substringBeforeLast('/', "")
    return VideoWithMetadata(
        video = VideoFile(name = name, path = path, folderPath = folder),
        title = name.substringBeforeLast('.', name),
        subtitle = source.displayName,
        posterUrl = posterUrl,
        backdropUrl = null,
        overview = overview,
        rating = rating?.toDouble(),
        type = "network",
    )
}
