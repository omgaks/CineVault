package com.sole.cinevault.subtitles

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Downloaded, generated and extracted subtitles live in CineVault's private storage, which no
 * file manager (and no file picker) can open. This keeps a visible copy in
 * Documents/CineVault/Subtitles, so "Open subtitle file…" can start in that folder and the files
 * can be shared or backed up. Needs no permission on Android 10+.
 */
object SubtitlePublicFolder {

    const val RELATIVE_PATH = "Documents/CineVault/Subtitles"
    const val DISPLAY_PATH = "Documents › CineVault › Subtitles"

    /** Where the system file picker should open (it falls back gracefully if the folder is new). */
    fun initialPickerUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$RELATIVE_PATH",
        )

    /** Readable name for a mirrored file, e.g. "The Incredibles.en.SubDL.srt". */
    internal fun friendlyName(videoPath: String, language: String, provider: String): String {
        val base = videoPath.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(90).ifBlank { "movie" }
        return "$base.$language.$provider.srt"
    }

    /**
     * Copies this movie's subtitle files (downloads, AI translations, generated and extracted
     * embedded subtitles) into the public folder. Existing files are left alone.
     * @return how many files were newly copied.
     */
    fun mirror(context: Context, videoPath: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            var copied = 0
            val videoBase = videoPath.substringAfterLast('/').substringBeforeLast('.')

            // 1) Downloads for THIS movie, under a readable name.
            val cacheDir = File(context.filesDir, "subtitles")
            val languages = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".srt") }
                ?.mapNotNull { it.name.removeSuffix(".srt").split('.').getOrNull(1) }
                ?.distinct().orEmpty()
            for (lang in languages) {
                for (provider in listOf("OpenSubtitles", "SubDL")) {
                    val file = OpenSubtitlesClient.subtitleCacheFile(context, videoPath, lang, provider)
                    if (file.isFile && file.length() > 0L) {
                        if (copyIfMissing(context, file, friendlyName(videoPath, lang, provider))) copied++
                    }
                }
            }

            // 2) AI-translated / generated and extracted subtitles are already named after the movie.
            for (dirName in listOf("generated_subs", "embedded_subs")) {
                File(context.filesDir, dirName).listFiles { f -> f.isFile && f.name.endsWith(".srt") }
                    ?.filter { it.name.startsWith(videoBase.take(80).replace(Regex("[^A-Za-z0-9 ._()\\[\\]-]"), "_")) ||
                        it.name.startsWith(videoBase) }
                    ?.forEach { if (copyIfMissing(context, it, it.name)) copied++ }
            }
            copied
        } catch (_: Exception) {
            0
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyIfMissing(context: Context, source: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val exists = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(displayName, "$RELATIVE_PATH/"),
            null,
        )?.use { it.count > 0 } ?: false
        if (exists) return false

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        val target = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            ?: return false
        return true
    }
}
