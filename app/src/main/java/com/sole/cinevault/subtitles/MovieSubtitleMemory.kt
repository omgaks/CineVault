package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest

/**
 * Per-video subtitle session memory.
 *
 * This deliberately sits beside the existing per-display profile system:
 * display profiles remain the defaults for a phone/tablet/external display,
 * while this memory restores the user's last choices for one specific video.
 */
data class MovieSubtitleMemory(
    val subtitlesEnabled: Boolean,
    val primaryUri: String?,
    val primaryLanguage: String?,
    val selectedKey: String?,
    val selectedLabel: String,
    val selectedSource: String,
    val dualEnabled: Boolean,
    val dualSecondaryLanguage: String,
    val dualGapLines: Int,
    val dualSecondarySource: String,
    val syncOffsetSeconds: Float,
    val textSizeSp: Float,
    val bottomPadding: Float,
    val presetName: String,
    val foregroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    val backgroundColor: Int,
    val preserveOriginalStyling: Boolean,
)

private const val MOVIE_SUBTITLE_PREFS = "cinevault_movie_subtitle_memory"

private fun movieSubtitleKey(videoPath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(videoPath.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
}

fun loadMovieSubtitleMemory(
    context: Context,
    videoPath: String,
): MovieSubtitleMemory? {
    val prefs = context.getSharedPreferences(MOVIE_SUBTITLE_PREFS, Context.MODE_PRIVATE)
    val key = movieSubtitleKey(videoPath)
    if (!prefs.getBoolean("$key.saved", false)) return null

    return MovieSubtitleMemory(
        subtitlesEnabled = prefs.getBoolean("$key.subtitlesEnabled", true),
        primaryUri = prefs.getString("$key.primaryUri", null),
        primaryLanguage = prefs.getString("$key.primaryLanguage", null),
        selectedKey = prefs.getString("$key.selectedKey", null),
        selectedLabel = prefs.getString("$key.selectedLabel", "") ?: "",
        selectedSource = prefs.getString("$key.selectedSource", "") ?: "",
        dualEnabled = prefs.getBoolean("$key.dualEnabled", false),
        dualSecondaryLanguage = prefs.getString("$key.dualSecondaryLanguage", "hi") ?: "hi",
        dualGapLines = prefs.getInt("$key.dualGapLines", 1).coerceIn(0, 2),
        dualSecondarySource = prefs.getString("$key.dualSecondarySource", "") ?: "",
        syncOffsetSeconds = prefs.getFloat("$key.syncOffsetSeconds", 0f),
        textSizeSp = prefs.getFloat("$key.textSizeSp", 18f),
        bottomPadding = prefs.getFloat("$key.bottomPadding", 0.02f),
        presetName = prefs.getString("$key.presetName", "CineVault") ?: "CineVault",
        foregroundColor = prefs.getInt("$key.foregroundColor", 0xFFFFF3D6.toInt()),
        edgeType = prefs.getInt("$key.edgeType", 1),
        edgeColor = prefs.getInt("$key.edgeColor", 0xFF000000.toInt()),
        backgroundColor = prefs.getInt("$key.backgroundColor", 0x00000000),
        preserveOriginalStyling = prefs.getBoolean("$key.preserveOriginalStyling", false),
    )
}

fun saveMovieSubtitleMemory(
    context: Context,
    videoPath: String,
    memory: MovieSubtitleMemory,
) {
    val prefs = context.getSharedPreferences(MOVIE_SUBTITLE_PREFS, Context.MODE_PRIVATE)
    val key = movieSubtitleKey(videoPath)

    prefs.edit {
        putBoolean("$key.saved", true)
        putBoolean("$key.subtitlesEnabled", memory.subtitlesEnabled)
        putString("$key.primaryUri", memory.primaryUri)
        putString("$key.primaryLanguage", memory.primaryLanguage)
        putString("$key.selectedKey", memory.selectedKey)
        putString("$key.selectedLabel", memory.selectedLabel)
        putString("$key.selectedSource", memory.selectedSource)
        putBoolean("$key.dualEnabled", memory.dualEnabled)
        putString("$key.dualSecondaryLanguage", memory.dualSecondaryLanguage)
        putInt("$key.dualGapLines", memory.dualGapLines.coerceIn(0, 2))
        putString("$key.dualSecondarySource", memory.dualSecondarySource)
        putFloat("$key.syncOffsetSeconds", memory.syncOffsetSeconds)
        putFloat("$key.textSizeSp", memory.textSizeSp)
        putFloat("$key.bottomPadding", memory.bottomPadding)
        putString("$key.presetName", memory.presetName)
        putInt("$key.foregroundColor", memory.foregroundColor)
        putInt("$key.edgeType", memory.edgeType)
        putInt("$key.edgeColor", memory.edgeColor)
        putInt("$key.backgroundColor", memory.backgroundColor)
        putBoolean("$key.preserveOriginalStyling", memory.preserveOriginalStyling)
    }
}

/**
 * Restores only URIs CineVault can still read. If a cache file was cleaned
 * by Android, the normal subtitle discovery path is allowed to take over.
 */
fun canRestoreMovieSubtitleUri(
    context: Context,
    uriText: String?,
): Boolean {
    if (uriText.isNullOrBlank()) return false

    return try {
        val uri = Uri.parse(uriText)
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let(::File)?.isFile == true
            "content" -> context.contentResolver.openInputStream(uri)?.use { true } ?: false
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}
