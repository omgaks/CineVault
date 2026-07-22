package com.sole.cinevault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── Restricted folders ──────────────────────────────────────────────────
// A folder added through Settings' "Add Restricted Folder" picker (distinct
// from the general "Add Media Folder" picker) — treated deliberately
// differently from a normal library folder:
//   - Scanned directly via SAF/DocumentFile traversal, bypassing BOTH the
//     10-minute/50MB floor and the isPersonalVideo() filename filter that
//     scanDeviceVideos() applies — both exist to keep accidental camera-roll
//     junk out of a normal scan, but this folder was deliberately picked by
//     the person, so those heuristics shouldn't apply to its contents.
//   - Grouped into a single poster card in Library (see restrictedFolderGroups
//     in LocalVideoLibraryScreen.kt) instead of listing every clip separately.
//   - Deliberately excluded from Home/Continue Watching (see
//     homeVisibleVideos in MainActivity.kt) — visible everywhere else,
//     no PIN, just never surfaced somewhere a passerby glancing at Home
//     would see it.
//   - No automatic subtitle search (see VideoPlayerScreen.kt) — only the
//     manual Download button works for files inside it.
//   - Never sent through TMDB enrichment — see VideoWithMetadata.type ==
//     "restricted", which the enrichment/upgrade checks in MetadataCache.kt
//     already only ever match against "movie"/"tv".
data class RestrictedFolder(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val treeUri: String, // persisted SAF tree URI, as a string
    val lastPlayedVideoPath: String? = null // drives the poster card's thumbnail
)

private const val RESTRICTED_FOLDERS_PREF = "cinevault_restricted_folders"
private const val RESTRICTED_FOLDERS_KEY = "folders"

fun loadRestrictedFolders(context: Context): List<RestrictedFolder> {
    val raw = context.getSharedPreferences(RESTRICTED_FOLDERS_PREF, Context.MODE_PRIVATE)
        .getString(RESTRICTED_FOLDERS_KEY, "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val treeUri = o.optString("treeUri")
                if (treeUri.isBlank()) continue
                add(
                    RestrictedFolder(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        displayName = o.optString("displayName").ifBlank { "Folder" },
                        treeUri = treeUri,
                        lastPlayedVideoPath = o.optString("lastPlayedVideoPath", "").ifBlank { null }
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveRestrictedFolders(context: Context, folders: List<RestrictedFolder>) {
    val array = JSONArray()
    folders.forEach { f ->
        array.put(
            JSONObject().apply {
                put("id", f.id)
                put("displayName", f.displayName)
                put("treeUri", f.treeUri)
                put("lastPlayedVideoPath", f.lastPlayedVideoPath ?: "")
            }
        )
    }
    context.getSharedPreferences(RESTRICTED_FOLDERS_PREF, Context.MODE_PRIVATE)
        .edit().putString(RESTRICTED_FOLDERS_KEY, array.toString()).apply()
}

fun addRestrictedFolder(context: Context, displayName: String, treeUri: String) {
    val current = loadRestrictedFolders(context).toMutableList()
    // Same tree picked twice — update the label instead of adding a duplicate.
    val existingIdx = current.indexOfFirst { it.treeUri == treeUri }
    if (existingIdx >= 0) {
        current[existingIdx] = current[existingIdx].copy(displayName = displayName)
    } else {
        current.add(RestrictedFolder(displayName = displayName, treeUri = treeUri))
    }
    saveRestrictedFolders(context, current)
}

fun removeRestrictedFolder(context: Context, id: String) {
    saveRestrictedFolders(context, loadRestrictedFolders(context).filterNot { it.id == id })
}

// ── Folder-membership marker ────────────────────────────────────────────
// VideoFile.folderPath already existed (used elsewhere for Secret-folder
// membership) but was blank for MediaStore-scanned items. Restricted-folder
// scanning deliberately writes a synthetic marker into it instead of a real
// filesystem path — SAF-scanned files only have content:// URIs, which have
// no reliable string-prefix relationship to their parent tree URI across
// different storage providers, so trying to *infer* folder membership from
// the path string later would be fragile. Tagging it directly at scan time
// (when the folder is already known) sidesteps that entirely.
fun restrictedFolderMarker(folderId: String): String = "restricted:$folderId"

fun folderIdFromRestrictedMarker(folderPath: String): String? =
    if (folderPath.startsWith("restricted:")) folderPath.removePrefix("restricted:") else null

fun isRestrictedFolderItem(item: VideoWithMetadata): Boolean =
    item.type == "restricted" || folderIdFromRestrictedMarker(item.video.folderPath) != null

// Called from VideoPlayerScreen.kt when playback of a restricted-folder item
// starts, so the folder's poster thumbnail always reflects the most
// recently played clip from it.
fun updateRestrictedFolderLastPlayed(context: Context, videoPath: String, folderPath: String) {
    val folderId = folderIdFromRestrictedMarker(folderPath) ?: return
    val folders = loadRestrictedFolders(context)
    val updated = folders.map { folder ->
        if (folder.id == folderId) folder.copy(lastPlayedVideoPath = videoPath) else folder
    }
    if (updated != folders) saveRestrictedFolders(context, updated)
}
