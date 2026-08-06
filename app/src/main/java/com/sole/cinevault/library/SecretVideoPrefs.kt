package com.sole.cinevault.library

import com.sole.cinevault.VideoWithMetadata

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

// FIX: this whole file used to be plain SharedPreferences — the entire
// point of Secret Folder is that these paths stay private, but they sat
// in cleartext, readable by anything with root access or a plain (non-
// encrypted) adb backup, same class of gap SmbShareStore.kt already fixed
// for SMB credentials. Same pattern reused here: EncryptedSharedPreferences,
// Keystore-backed, with a one-time migration off the old plaintext file so
// existing users don't lose their configured secret paths on upgrade.
private const val SECRET_PREFS_NAME_SECURE = "cinevault_secret_secure"
private const val SECRET_PREFS_NAME_LEGACY = "cinevault_secret"
private const val SECRET_PATHS_KEY = "secret_video_paths"
private const val SECRET_FOLDERS_KEY = "secret_folder_paths"

private fun securePrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        SECRET_PREFS_NAME_SECURE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

// Copies whatever's in the old plaintext file into the encrypted store,
// then wipes the old file — leaving stale plaintext secret paths sitting
// on disk unused forever would defeat the point of encrypting anything
// going forward. Never overwrites anything already saved in the secure
// store, and leaves the legacy file alone if migration fails for any
// reason rather than risk losing saved paths outright.
private fun migrateLegacySecretPrefsIfNeeded(context: Context) {
    val legacy = context.getSharedPreferences(SECRET_PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
    val legacyPaths = legacy.getStringSet(SECRET_PATHS_KEY, null)
    val legacyFolders = legacy.getStringSet(SECRET_FOLDERS_KEY, null)
    if (legacyPaths.isNullOrEmpty() && legacyFolders.isNullOrEmpty()) {
        if (legacyPaths != null || legacyFolders != null) legacy.edit().clear().apply()
        return
    }
    try {
        val secure = securePrefs(context)
        val editor = secure.edit()
        if (!legacyPaths.isNullOrEmpty() && secure.getStringSet(SECRET_PATHS_KEY, null).isNullOrEmpty()) {
            editor.putStringSet(SECRET_PATHS_KEY, legacyPaths)
        }
        if (!legacyFolders.isNullOrEmpty() && secure.getStringSet(SECRET_FOLDERS_KEY, null).isNullOrEmpty()) {
            editor.putStringSet(SECRET_FOLDERS_KEY, legacyFolders)
        }
        editor.apply()
        legacy.edit().clear().apply()
    } catch (_: Exception) {
        // Leave the legacy file in place — load functions below still work
        // against the encrypted store either way, so a failed migration
        // just means existing secret paths need re-adding manually.
    }
}

fun loadSecretVideoPaths(context: Context): Set<String> {
    migrateLegacySecretPrefsIfNeeded(context)
    return securePrefs(context)
        .getStringSet(SECRET_PATHS_KEY, emptySet<String>())
        ?.toSet()
        ?: emptySet()
}

fun saveSecretVideoPaths(context: Context, paths: Set<String>) {
    securePrefs(context)
        .edit()
        .putStringSet(SECRET_PATHS_KEY, paths)
        .apply()
}

fun loadSecretFolderPaths(context: Context): Set<String> {
    migrateLegacySecretPrefsIfNeeded(context)
    return securePrefs(context)
        .getStringSet(SECRET_FOLDERS_KEY, emptySet<String>())
        ?.toSet()
        ?: emptySet()
}

fun saveSecretFolderPaths(context: Context, paths: Set<String>) {
    securePrefs(context)
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
