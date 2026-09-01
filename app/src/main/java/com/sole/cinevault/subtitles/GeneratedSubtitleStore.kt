package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import java.io.File

data class GeneratedSubtitleFile(
    val uri: Uri,
    val fileName: String,
    val label: String,
    val cueCount: Int,
    val modifiedAt: Long,
)

/**
 * Small persistent catalogue for AI-generated / translated subtitle files.
 *
 * Files remain in CineVault's private generated-subtitles folder and survive
 * normal app restarts/updates. The player can list them again for the same
 * video without asking the user to hunt through Android storage.
 */
object GeneratedSubtitleStore {

    private const val DIR_NAME = "generated-subtitles"

    fun directory(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun videoBaseName(videoPath: String): String =
        videoPath
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "subtitle" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)

    fun listForVideo(
        context: Context,
        videoPath: String,
    ): List<GeneratedSubtitleFile> {
        val base = videoBaseName(videoPath)
        return directory(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("srt", ignoreCase = true) }
            .filter { it.name.startsWith(base) }
            .map { file ->
                GeneratedSubtitleFile(
                    uri = Uri.fromFile(file),
                    fileName = file.name,
                    label = labelForFile(base, file.name),
                    cueCount = countCues(file),
                    modifiedAt = file.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedAt }
            .toList()
    }

    fun write(
        context: Context,
        videoPath: String,
        srtText: String,
        labelSuffix: String,
    ): GeneratedSubtitleFile? {
        return try {
            val base = videoBaseName(videoPath)
            val safeSuffix = labelSuffix
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_')
                .ifBlank { "ai-auto" }

            val file = File(
                directory(context),
                "$base-$safeSuffix-${System.currentTimeMillis()}.srt",
            )
            file.writeText(srtText)

            GeneratedSubtitleFile(
                uri = Uri.fromFile(file),
                fileName = file.name,
                label = labelForFile(base, file.name),
                cueCount = countCues(file),
                modifiedAt = file.lastModified(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun labelForFile(
        base: String,
        fileName: String,
    ): String {
        val withoutExtension = fileName.removeSuffix(".srt")
        val middle = withoutExtension
            .removePrefix("$base-")
            .replace(Regex("-\\d{10,}$"), "")

        return when {
            middle.startsWith("ai-", ignoreCase = true) -> {
                val language = middle.removePrefix("ai-")
                    .replace('_', ' ')
                    .ifBlank { "Auto" }
                "AI ${language.prettyLanguageCode()}"
            }

            middle.startsWith("translated-", ignoreCase = true) -> {
                val language = middle.removePrefix("translated-")
                    .replace('_', ' ')
                    .ifBlank { "Translation" }
                language.prettyLanguageCode()
            }

            else -> "AI Subtitle"
        }
    }

    private fun String.prettyLanguageCode(): String {
        return when (lowercase()) {
            "en", "eng", "english" -> "English"
            "es", "spa", "spanish" -> "Spanish"
            "fr", "fra", "fre", "french" -> "French"
            "de", "deu", "ger", "german" -> "German"
            "pt", "por", "portuguese" -> "Portuguese"
            "hi", "hin", "hindi" -> "Hindi"
            "ja", "jpn", "japanese" -> "Japanese"
            "ko", "kor", "korean" -> "Korean"
            "zh", "zho", "chi", "chinese" -> "Chinese"
            "ar", "ara", "arabic" -> "Arabic"
            "ru", "rus", "russian" -> "Russian"
            "it", "ita", "italian" -> "Italian"
            "ta", "tam", "tamil" -> "Tamil"
            "te", "tel", "telugu" -> "Telugu"
            "bn", "ben", "bengali" -> "Bengali"
            "auto", "" -> "Auto"
            else -> split(' ', '-', '_')
                .joinToString(" ") { token ->
                    token.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase() else c.toString()
                    }
                }
        }
    }

    private fun countCues(file: File): Int {
        return try {
            file.useLines { lines ->
                lines.count { line -> line.contains(" --> ") }
            }
        } catch (_: Exception) {
            -1
        }
    }
}
