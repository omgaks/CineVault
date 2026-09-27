package com.sole.cinevault.network

import androidx.compose.runtime.Composable
import com.sole.cinevault.library.VideoFile

/**
 * D16-R2-S9B — final app-navigation bridge for Network Hub -> Remote Library.
 *
 * MainActivity only needs to hold a NetworkSource destination and render this
 * composable. The remote library itself remains adaptive and protocol-neutral.
 */
@Composable
fun NetworkRemoteLibraryDestination(
    source: NetworkSource,
    onBack: () -> Unit,
    onPlayVideo: (VideoFile) -> Unit,
) {
    RemoteLibraryScreen(
        source = source,
        onBack = onBack,
        onPlay = { video ->
            onPlayVideo(video.toCineVaultVideoFile())
        },
    )
}

/**
 * NetworkVideo paths are deliberately passed unchanged.
 *
 * - smb:// continues through CineVault's SMB DataSource router.
 * - http(s):// continues through Media3's normal network DataSource.
 * - protocol engines that expose a playable URI therefore enter the same
 *   VideoPlayerScreen pipeline as local/stream playback.
 */
internal fun NetworkVideo.toCineVaultVideoFile(): VideoFile =
    VideoFile(
        name = name,
        path = path,
        folderPath = "",
    )
