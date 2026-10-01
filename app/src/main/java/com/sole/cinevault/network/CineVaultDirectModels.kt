package com.sole.cinevault.network

import com.google.gson.annotations.SerializedName
import java.io.File

data class CineVaultDirectMedia(
    val id: String,
    val title: String,
    val file: File,
    val folderId: String?,
    val mimeType: String = "video/*",
    val subtitleFiles: List<File> = emptyList(),
    val posterFile: File? = null,
    val isVaultOrSecret: Boolean = false,
)

data class CineVaultDirectCatalogueItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("sizeBytes") val sizeBytes: Long,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("streamPath") val streamPath: String,
    @SerializedName("subtitlePaths") val subtitlePaths: List<String>,
    @SerializedName("artworkPath") val artworkPath: String?,
)

data class CineVaultDirectCatalogue(
    @SerializedName("protocolVersion") val protocolVersion: Int = CineVaultLanProtocol.VERSION,
    @SerializedName("items") val items: List<CineVaultDirectCatalogueItem>,
)

fun buildDirectCatalogue(
    media: List<CineVaultDirectMedia>,
    selection: ShareLibrarySelection,
): CineVaultDirectCatalogue {
    val allowedIds = filterShareableLibrary(
        media.map {
            ShareableLibraryItem(
                id = it.id,
                path = it.file.absolutePath,
                folderId = it.folderId,
                isVaultOrSecret = it.isVaultOrSecret,
            )
        },
        selection,
    ).mapTo(hashSetOf()) { it.id }

    return CineVaultDirectCatalogue(
        items = media.asSequence()
            .filter { it.id in allowedIds && it.file.isFile }
            .map { mediaItem ->
                CineVaultDirectCatalogueItem(
                    id = mediaItem.id,
                    title = mediaItem.title,
                    sizeBytes = mediaItem.file.length(),
                    mimeType = mediaItem.mimeType,
                    streamPath = "/v1/media/${urlSegment(mediaItem.id)}",
                    subtitlePaths = mediaItem.subtitleFiles.filter(File::isFile).mapIndexed { index, _ ->
                        "/v1/media/${urlSegment(mediaItem.id)}/subtitle/$index"
                    },
                    artworkPath = mediaItem.posterFile?.takeIf(File::isFile)?.let {
                        "/v1/media/${urlSegment(mediaItem.id)}/artwork"
                    },
                )
            }
            .toList(),
    )
}

internal fun urlSegment(value: String): String =
    java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
