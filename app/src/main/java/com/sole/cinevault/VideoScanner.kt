package com.sole.cinevault

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun scanDeviceVideos(context: Context): List<VideoWithMetadata> =
    withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoFile>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        // Only videos longer than 10 minutes (600,000ms) and larger than 50MB
        // This filters out camera clips, WhatsApp videos, screen recordings etc.
        val selection =
            "${MediaStore.Video.Media.DURATION} >= ? AND ${MediaStore.Video.Media.SIZE} >= ?"
        val selectionArgs = arrayOf(
            "600000",           // 10 minutes in ms
            "${50 * 1024 * 1024}" // 50 MB
        )

        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)

                    // Skip if file doesn't actually exist
                    if (!File(path).exists()) continue

                    // Skip obvious personal/camera files by name pattern
                    if (isPersonalVideo(name)) continue

                    // Skip scan sources exclusions (user-configured hidden folders)
                    if (!isVideoAllowedByScanSources(context, path)) continue

                    results.add(
                        VideoFile(
                            path = path,
                            name = name,
                            size = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CineVault", "Scan failed: ${e.message}", e)
        }

        // Convert to VideoWithMetadata with basic info
        results.map { videoFile ->
            VideoWithMetadata(
                video = videoFile,
                title = cleanScannedTitle(videoFile.name),
                subtitle = "",
                posterUrl = null,
                backdropUrl = null,
                overview = null,
                rating = null,
                type = guessMediaType(videoFile.name)
            )
        }
    }

// Only block obviously personal/camera content by filename pattern
// Much less aggressive than before — only blocks clear non-movie files
private fun isPersonalVideo(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return when {
        // Camera roll patterns
        lower.matches(Regex("^(vid|img|dsc|dcim|cam)_\\d{8}_\\d{6}.*")) -> true
        lower.matches(Regex("^\\d{8}_\\d{6}.*")) -> true // bare timestamp
        lower.startsWith("whatsapp video") -> true
        lower.startsWith("whatsapp animated gif") -> true
        lower.contains("received_") -> true
        lower.startsWith("screen_record") || lower.startsWith("screenrecord") -> true
        lower.startsWith("instagram_") -> true
        lower.startsWith("snapchat-") -> true
        lower.startsWith("tiktok_") -> true
        else -> false
    }
}

private fun cleanScannedTitle(fileName: String): String {
    var title = fileName.substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ")
        .replace(Regex("\\(\\d{4}\\)"), " ")
        .replace(".", " ").replace("_", " ").replace("-", " ")

    // Strip common release tags
    title = title.replace(
        Regex(
            "\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|imax|remux|" +
            "bluray|blu.?ray|brrip|hdrip|webrip|web.?dl|webdl|web|nf|amzn|dsnp|hulu|" +
            "x264|x265|h264|h265|hevc|10bit|8bit|aac|ddp|dts|truehd|atmos|" +
            "yts|rarbg|tgx|eztv|pir8|proper|repack|extended|theatrical|" +
            "directors?.?cut|multi|dual|eng|hindi|ita|mkv|mp4|avi|subs?)\\b",
            RegexOption.IGNORE_CASE
        ), " "
    )
    title = title.replace(Regex("\\bS\\d{1,2}E\\d{1,2}\\b", RegexOption.IGNORE_CASE), " ")
    title = title.replace(Regex("\\b(19|20)\\d{2}\\b.*$"), " ")
    return title.replace(Regex("\\s+"), " ").trim().ifBlank { fileName.substringBeforeLast(".") }
}

private fun guessMediaType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "tv"
        lower.contains("season") || lower.contains("episode") -> "tv"
        else -> "movie"
    }
}
