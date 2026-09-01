package com.sole.cinevault

import android.content.Context
import android.widget.Toast
import com.sole.cinevault.library.clearPlaybackPosition
import com.sole.cinevault.library.saveFavoriteVideoPaths
import com.sole.cinevault.library.saveSecretFolderPaths
import com.sole.cinevault.library.saveSecretVideoPaths
import java.io.File

internal fun addVideoToSecret(
    context: Context,
    hiddenPaths: Set<String>,
    item: VideoWithMetadata
): Set<String> {
    val updated = hiddenPaths + item.video.path
    saveSecretVideoPaths(context, updated)
    clearPlaybackPosition(context, item.video.path)
    Toast.makeText(
        context,
        "Moved to Secret folder",
        Toast.LENGTH_SHORT
    ).show()
    return updated
}

internal fun removeVideoFromSecret(
    context: Context,
    hiddenPaths: Set<String>,
    item: VideoWithMetadata
): Set<String> {
    val updated = hiddenPaths - item.video.path
    saveSecretVideoPaths(context, updated)
    Toast.makeText(
        context,
        "Removed from Secret folder",
        Toast.LENGTH_SHORT
    ).show()
    return updated
}

internal fun removeContainingFolderFromSecret(
    context: Context,
    hiddenFolders: Set<String>,
    item: VideoWithMetadata
): Set<String>? {
    val folderPath = hiddenFolders.firstOrNull {
        item.video.path.startsWith(it)
    } ?: File(item.video.path).parent ?: return null

    val updated = hiddenFolders - folderPath
    saveSecretFolderPaths(context, updated)
    Toast.makeText(
        context,
        "Folder removed from Secret",
        Toast.LENGTH_SHORT
    ).show()
    return updated
}

internal fun toggleVideosInSecretFolder(
    context: Context,
    hiddenPaths: Set<String>,
    folderVideoPaths: List<String>,
    onLongPressHaptic: () -> Unit
): Set<String>? {
    if (folderVideoPaths.isEmpty()) return null

    onLongPressHaptic()

    val pathSet = folderVideoPaths.toSet()
    val allHidden = pathSet.all { hiddenPaths.contains(it) }
    val updated =
        if (allHidden) hiddenPaths - pathSet
        else hiddenPaths + pathSet

    saveSecretVideoPaths(context, updated)
    Toast.makeText(
        context,
        if (allHidden) {
            "Folder removed from Secret"
        } else {
            "Folder moved to Secret"
        },
        Toast.LENGTH_SHORT
    ).show()

    return updated
}

internal fun addVideoToFavorites(
    context: Context,
    favoritePaths: Set<String>,
    item: VideoWithMetadata
): Set<String> {
    val updated = favoritePaths + item.video.path
    saveFavoriteVideoPaths(context, updated)
    Toast.makeText(
        context,
        "Added to Favorites",
        Toast.LENGTH_SHORT
    ).show()
    return updated
}

internal fun removeVideoFromFavorites(
    context: Context,
    favoritePaths: Set<String>,
    item: VideoWithMetadata
): Set<String> {
    val updated = favoritePaths - item.video.path
    saveFavoriteVideoPaths(context, updated)
    Toast.makeText(
        context,
        "Removed from Favorites",
        Toast.LENGTH_SHORT
    ).show()
    return updated
}
