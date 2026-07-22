package com.sole.cinevault

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RESTRICTED_VIDEO_EXTENSIONS = setOf(
    "mkv", "mp4", "avi", "mov", "m4v", "ts", "webm", "wmv", "flv", "m2ts", "3gp"
)

// Guards against pathological nesting the same way SmbVideoScanner.kt does
// for network shares.
private const val MAX_RESTRICTED_SCAN_DEPTH = 6

suspend fun scanRestrictedFolder(context: Context, folder: RestrictedFolder): List<VideoWithMetadata> =
    withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoWithMetadata>()
        try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUri))
            if (root == null || !root.exists() || !root.isDirectory) {
                // Persisted SAF permission can be revoked by the system (or
                // the folder moved/deleted) — fail this one folder quietly
                // rather than crash the whole library scan over it.
                Log.w("CineVault", "Restricted folder \"${folder.displayName}\" is no longer accessible")
                return@withContext emptyList()
            }
            walkRestrictedFolder(root, folder, depth = 0, out = results)
        } catch (e: Exception) {
            Log.e("CineVault", "Restricted folder scan failed for ${folder.displayName}: ${e.message}", e)
        }
        results
    }

suspend fun scanAllRestrictedFolders(context: Context): List<VideoWithMetadata> =
    withContext(Dispatchers.IO) {
        loadRestrictedFolders(context).flatMap { scanRestrictedFolder(context, it) }
    }

// Deliberately does NOT apply scanDeviceVideos()'s 10-minute/50MB floor or
// its isPersonalVideo() filename filter (which would reject files starting
// with "tiktok_", among others) — both exist to keep accidental camera-roll
// junk out of a NORMAL scan, but this folder was picked on purpose, so a
// short/small clip here is exactly what's wanted, not something to exclude.
private fun walkRestrictedFolder(dir: DocumentFile, folder: RestrictedFolder, depth: Int, out: MutableList<VideoWithMetadata>) {
    if (depth > MAX_RESTRICTED_SCAN_DEPTH) return

    val children = try {
        dir.listFiles()
    } catch (e: Exception) {
        Log.w("CineVault", "Restricted folder: couldn't list a subfolder: ${e.message}")
        return
    }

    for (child in children) {
        try {
            when {
                child.isDirectory -> walkRestrictedFolder(child, folder, depth + 1, out)
                child.isFile -> {
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in RESTRICTED_VIDEO_EXTENSIONS) continue

                    val videoFile = VideoFile(
                        path = child.uri.toString(),
                        name = name,
                        folderPath = restrictedFolderMarker(folder.id)
                    )
                    out.add(
                        VideoWithMetadata(
                            video = videoFile,
                            title = cleanScannedTitle(name),
                            subtitle = "",
                            posterUrl = null,
                            backdropUrl = null,
                            overview = null,
                            rating = null,
                            // Distinct from "movie"/"tv"/"local" — keeps
                            // these out of the TMDB enrichment pipeline
                            // entirely (needsRatingsUpgrade/needsGenreUpgrade
                            // in MetadataCache.kt only ever match "movie" or
                            // "tv"), since running a TikTok clip's filename
                            // through a movie-title search would just waste
                            // an API call on a nonsense match.
                            type = "restricted"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("CineVault", "Restricted folder: skipped an entry: ${e.message}")
        }
    }
}
