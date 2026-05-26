package com.sole.cinevault

import android.content.Context
import java.io.File

private const val SECRET_PREFS_NAME = "cinevault_secret"
private const val SECRET_PATHS_KEY = "secret_video_paths"
private const val SECRET_FOLDERS_KEY = "secret_folder_paths"

fun loadSecretVideoPaths(context: Context): Set<String> {
    return context
        .getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(SECRET_PATHS_KEY, emptySet<String>())
        ?.toSet()
        ?: emptySet()
}

fun saveSecretVideoPaths(context: Context, paths: Set<String>) {
    context
        .getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(SECRET_PATHS_KEY, paths)
        .apply()
}

fun loadSecretFolderPaths(context: Context): Set<String> {
    return context
        .getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(SECRET_FOLDERS_KEY, emptySet<String>())
        ?.toSet()
        ?: emptySet()
}

fun saveSecretFolderPaths(context: Context, paths: Set<String>) {
    context
        .getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(SECRET_FOLDERS_KEY, paths)
        .apply()
}

fun videoIsInsideSecretFolder(videoPath: String, secretFolders: Set<String>): Boolean {
    return secretFolders.any { folder ->
        folder.isNotBlank() && videoPath.startsWith(folder)
    }
}

fun videoIsInsideSecretFolder(item: VideoWithMetadata, secretFolders: Set<String>): Boolean {
    val folderPath = item.video.folderPath ?: ""
    val fallback = item.video.path ?: ""

    return secretFolders.any { folder ->
        folder.isNotBlank() &&
                (folderPath.startsWith(folder) || fallback.startsWith(folder))
    }
}

fun getVideoFolderKey(item: VideoWithMetadata): String {
    return (item.video.folderPath ?: "").ifBlank {
        File(item.video.path ?: "").parent ?: ""
    }
}

fun createNoMediaFileForFolder(folderPath: String): Boolean {
    return try {
        if (folderPath.isBlank() || folderPath.startsWith("content://")) return false

        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return false

        val noMediaFile = File(folder, ".nomedia")
        if (!noMediaFile.exists()) {
            noMediaFile.createNewFile()
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }
}
