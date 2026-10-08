package com.sole.cinevault.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sole.cinevault.BuildConfig
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.metadata.ArtworkKind
import com.sole.cinevault.metadata.applyManualArtworkPreference
import com.sole.cinevault.metadata.applyRematch
import com.sole.cinevault.metadata.artworkstudio.ArtworkLocalStore
import com.sole.cinevault.metadata.artworkstudio.ArtworkStudioResult
import com.sole.cinevault.metadata.candidateFromTmdbId
import com.sole.cinevault.metadata.loadArtworkPreference
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import com.sole.cinevault.metadata.saveManualArtworkChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Folders the person chose to let CineVault look in for poster / fanart / NFO files. */
object LocalArtworkFolders {
    private const val PREFS = "cinevault_local_artwork"
    private const val KEY = "tree_uris"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, list: List<String>) {
        val array = JSONArray()
        list.distinct().forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun add(context: Context, uri: String) = save(context, load(context) + uri)
    fun remove(context: Context, uri: String) = save(context, load(context) - uri)
}

data class LocalArtworkReport(
    val checked: Int = 0,
    val posters: Int = 0,
    val backdrops: Int = 0,
    val nfoMatches: Int = 0,
    val outsideFolders: Int = 0,
    val noFolders: Boolean = false,
) {
    fun summary(): String = when {
        noFolders -> "Add a folder first."
        checked == 0 -> "None of your films are inside the folders you added."
        posters + backdrops + nfoMatches == 0 -> "Checked $checked videos. No new local artwork found."
        else -> buildString {
            append("Checked $checked videos: $posters posters, $backdrops backdrops")
            if (nfoMatches > 0) append(", $nfoMatches matched from NFO files")
            append(".")
        }
    }
}

/**
 * Reads artwork and NFO files that sit beside your videos, through the folder
 * access you granted (no broad storage permission). Anything found is copied
 * into CineVault's private storage and saved as your artwork choice. It never
 * overwrites a choice you already made, so running it again is safe.
 */
object LocalArtworkImporter {
    private data class Child(val docId: String, val name: String, val isDir: Boolean)

    suspend fun import(
        context: Context,
        videos: List<VideoWithMetadata>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Pair<List<VideoWithMetadata>, LocalArtworkReport> = withContext(Dispatchers.IO) {
        val trees = LocalArtworkFolders.load(context).mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        if (trees.isEmpty()) return@withContext videos to LocalArtworkReport(noFolders = true)

        val resolver = context.contentResolver
        val treeIds = HashMap<Uri, String>()
        trees.forEach { t -> runCatching { DocumentsContract.getTreeDocumentId(t) }.getOrNull()?.let { treeIds[t] = it } }

        val listings = HashMap<String, List<Child>>()
        fun children(tree: Uri, docId: String): List<Child> =
            listings.getOrPut("$tree|$docId") { queryChildren(resolver, tree, docId) }

        val located = videos.map { it to safLocationOf(it.video.path) }
        val siblings = located.mapNotNull { it.second }.groupingBy { it.volume to it.dir }.eachCount()
        val metadataOn = loadMetadataFetchEnabled(context) && BuildConfig.TMDB_TOKEN.isNotBlank()

        val updated = videos.toMutableList()
        var checked = 0; var posters = 0; var backdrops = 0; var nfo = 0; var outside = 0
        var changed = false

        located.forEachIndexed { index, (item, loc) ->
            if (index % 8 == 0) onProgress(index, videos.size)
            if (loc == null || (item.type != "movie" && item.type != "tv")) return@forEachIndexed
            val tree = trees.firstOrNull { t -> treeIds[t]?.let { treeCovers(it, loc.volume, loc.dir) } == true }
            val treeId = tree?.let { treeIds[it] }
            if (tree == null || treeId == null) { outside++; return@forEachIndexed }
            checked++

            val isTv = item.type == "tv"
            val ownSiblings = siblings[loc.volume to loc.dir] ?: 1
            val dirs = buildList {
                add(loc.dir to (isTv || ownSiblings == 1))
                if (isTv) loc.parentDir?.takeIf { treeCovers(treeId, loc.volume, it) }?.let { add(it to true) }
            }
            var current = item

            // 1) An NFO is the person's own statement of which film this is.
            if (metadataOn && !isTv) {
                val (dir, generic) = dirs.first()
                val kids = children(tree, safDocumentId(loc.volume, dir))
                val nfoName = SidecarNames.pick(kids.filter { !it.isDir }.map { it.name }, SidecarNames.nfoNames(loc.fileName, generic), listOf("nfo"))
                val nfoChild = kids.firstOrNull { it.name == nfoName }
                val id = nfoChild?.let { readHead(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.docId)) }
                    ?.let(::parseNfoTmdbId)
                if (id != null && id != current.tmdbId) {
                    val candidate = candidateFromTmdbId(context, id)
                    if (candidate != null) {
                        current = runCatching { applyRematch(context, current, candidate) }.getOrDefault(current)
                        if (current !== item) nfo++
                    }
                }
            }

            // 2) Artwork next to the video (never replaces a choice already made).
            val preference = loadArtworkPreference(context, current.video.path)
            for (kind in ArtworkKind.entries) {
                val alreadyChosen = if (kind == ArtworkKind.POSTER) preference?.manualPosterUrl else preference?.manualBackdropUrl
                if (alreadyChosen != null) continue
                for ((dir, generic) in dirs) {
                    val kids = children(tree, safDocumentId(loc.volume, dir))
                    val names = if (kind == ArtworkKind.POSTER) SidecarNames.posterNames(loc.fileName, generic)
                    else SidecarNames.backdropNames(loc.fileName, generic)
                    val pickedName = SidecarNames.pick(kids.filter { !it.isDir }.map { it.name }, names) ?: continue
                    val child = kids.firstOrNull { it.name == pickedName } ?: continue
                    val source = DocumentsContract.buildDocumentUriUsingTree(tree, child.docId)
                    val imported = ArtworkLocalStore.importImage(context, current.video.path, kind, source)
                    if (imported is ArtworkStudioResult.Success) {
                        saveManualArtworkChoice(context, current.video.path, kind, imported.value)
                        if (kind == ArtworkKind.POSTER) posters++ else backdrops++
                        break
                    }
                }
            }

            val shown = applyManualArtworkPreference(context, current)
            if (shown != item) { updated[index] = shown; changed = true }
        }

        onProgress(videos.size, videos.size)
        if (changed) saveLibraryCache(context, updated)
        updated.toList() to LocalArtworkReport(checked, posters, backdrops, nfo, outside)
    }

    private fun queryChildren(resolver: ContentResolver, tree: Uri, parentDocId: String): List<Child> = runCatching {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val out = ArrayList<Child>()
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                out += Child(c.getString(0), name, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        out
    }.getOrDefault(emptyList())

    /** First 64 KB as text; NFO files are tiny and nothing else is ever read. */
    private fun readHead(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            val n = input.read(buffer)
            if (n <= 0) null else String(buffer, 0, n, Charsets.UTF_8)
        }
    }.getOrNull()
}
