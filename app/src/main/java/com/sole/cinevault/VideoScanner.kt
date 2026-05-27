package com.sole.cinevault

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun scanDeviceVideos(
    context: Context
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {

    val videoList = mutableListOf<VideoWithMetadata>()

    val scannedCache = loadScannedVideoPaths(context).toMutableSet()
    val newScannedPaths = mutableSetOf<String>()

    scanMediaStoreVideos(
        context = context,
        videoList = videoList,
        scannedCache = scannedCache,
        newScannedPaths = newScannedPaths
    )

    scanCustomFolderVideos(
        context = context,
        videoList = videoList,
        scannedCache = scannedCache,
        newScannedPaths = newScannedPaths
    )

    saveScannedVideoPaths(
        context = context,
        paths = scannedCache + newScannedPaths
    )

    videoList.distinctBy { it.video.path }
}

private fun scanMediaStoreVideos(
    context: Context,
    videoList: MutableList<VideoWithMetadata>,
    scannedCache: Set<String>,
    newScannedPaths: MutableSet<String>
) {
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
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
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

            val scanCheckPath = "$folderPath$name"

            if (shouldSkipCineVaultScanPath(scanCheckPath)) {
                continue
            }

            if (!isVideoAllowedByScanSources(context, scanCheckPath)) {
                continue
            }

            if (scannedCache.contains(scanCheckPath)) {
                continue
            }

            videoList.add(
                createVideoMetadataItem(
                    name = name,
                    path = contentUri.toString(),
                    folderPath = folderPath
                )
            )

            newScannedPaths.add(scanCheckPath)
        }
    }
}

private fun scanCustomFolderVideos(
    context: Context,
    videoList: MutableList<VideoWithMetadata>,
    scannedCache: Set<String>,
    newScannedPaths: MutableSet<String>
) {
    val customFolders = loadCustomScanFolders(context)

    customFolders.forEach { folderUriString ->

        try {
            val folderUri = Uri.parse(folderUriString)

            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
                )

            val cursor =
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )

            cursor?.use { cursorData ->

                val idColumn =
                    cursorData.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )

                val nameColumn =
                    cursorData.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )

                val mimeColumn =
                    cursorData.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )

                while (cursorData.moveToNext()) {

                    val documentId = cursorData.getString(idColumn)
                    val name = cursorData.getString(nameColumn) ?: continue
                    val mimeType = cursorData.getString(mimeColumn) ?: ""

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        continue
                    }

                    if (!isSupportedVideoFile(name, mimeType)) {
                        continue
                    }

                    val fileUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                            folderUri,
                            documentId
                        )

                    val scanCheckPath = "$folderUriString/$name"

                    if (shouldSkipCineVaultScanPath(scanCheckPath)) {
                        continue
                    }

                    if (scannedCache.contains(scanCheckPath)) {
                        continue
                    }

                    videoList.add(
                        createVideoMetadataItem(
                            name = name,
                            path = fileUri.toString(),
                            folderPath = folderUriString
                        )
                    )

                    newScannedPaths.add(scanCheckPath)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun createVideoMetadataItem(
    name: String,
    path: String,
    folderPath: String
): VideoWithMetadata {

    val episodeInfo = extractEpisodeInfo(name)
    val isTv = episodeInfo != null

    val cleanedTitle =
        if (isTv) {
            episodeInfo?.showName ?: cleanMovieFilename(name)
        } else {
            cleanMovieFilename(name)
        }

    return VideoWithMetadata(
        video = VideoFile(
            name = name,
            path = path,
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
}

private fun isSupportedVideoFile(
    name: String,
    mimeType: String
): Boolean {
    val lower = name.lowercase()

    return mimeType.startsWith("video/") ||
            lower.endsWith(".mp4") ||
            lower.endsWith(".mkv") ||
            lower.endsWith(".avi") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".m4v") ||
            lower.endsWith(".3gp") ||
            lower.endsWith(".ts")
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
            lower.contains("/screenshots/") ||
            lower.contains("/instagram/") ||
            lower.contains("/facebook/") ||
            lower.contains("/cache/") ||
            lower.contains("/temp/")
}