package com.sole.cinevault

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun scanDeviceVideos(
    context: Context
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {

    val videoList = mutableListOf<VideoWithMetadata>()

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.DATE_ADDED
    )

    val cursor = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null
    )

    cursor?.use { cursorData ->

        val idColumn =
            cursorData.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

        val nameColumn =
            cursorData.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

        val relativePathColumn =
            cursorData.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

        while (cursorData.moveToNext()) {

            val id = cursorData.getLong(idColumn)

            val contentUri =
                ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

            val name =
                cursorData.getString(nameColumn)
                    ?: "Video_$id.mp4"

            val folderPath =
                if (relativePathColumn >= 0) {
                    cursorData.getString(relativePathColumn) ?: ""
                } else {
                    ""
                }

            val scanCheckPath = "$folderPath/$name"

            if (shouldSkipCineVaultScanPath(scanCheckPath)) {
                continue
            }

            val episodeInfo = extractEpisodeInfo(name)
            val isTv = episodeInfo != null
            val cleanedTitle =
                if (isTv) {
                    episodeInfo?.showName ?: cleanMovieFilename(name)
                } else {
                    cleanMovieFilename(name)
                }

            videoList.add(
                VideoWithMetadata(
                    video = VideoFile(
                        name = name,
                        path = contentUri.toString(),
                        folderPath = folderPath
                    ),
                    title = cleanedTitle,
                    subtitle =
                        if (isTv && episodeInfo != null) {
                            "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')}"
                        } else {
                            "Movie"
                        },
                    posterUrl = null,
                    backdropUrl = null,
                    episodeStill = null,
                    overview = "",
                    rating = 0.0,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    tmdbId = null,
                    type = if (isTv) "tv" else "movie"
                )
            )
        }
    }

    videoList
}

private fun shouldSkipCineVaultScanPath(path: String): Boolean {
    val lower = path.lowercase()

    return lower.contains("/whatsapp/") ||
            lower.contains("whatsapp video") ||
            lower.contains("/android/media/com.whatsapp/") ||
            lower.contains("/whatsapp business/") ||
            lower.contains("/android/media/com.whatsapp.w4b/") ||
            lower.contains("/telegram/") ||
            lower.contains("/screenrecord") ||
            lower.contains("/screen_record") ||
            lower.contains("/screenshots/")
}
