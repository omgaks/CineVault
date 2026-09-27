package com.sole.cinevault.network

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
    val id: String,
    val title: String,
    val sizeBytes: Long,
    val mimeType: String,
    val streamPath: String,
    val subtitlePaths: List<String>,
    val artworkPath: String?,
)

data class CineVaultDirectCatalogue(
    val protocolVersion: Int = CineVaultLanProtocol.VERSION,
    val items: List<CineVaultDirectCatalogueItem>,
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
            .map {
                CineVaultDirectCatalogueItem(
                    id = it.id,
                    title = it.title,
                    sizeBytes = it.file.length(),
                    mimeType = it.mimeType,
                    streamPath = "/v1/media/${urlSegment(it.id)}",
                    subtitlePaths = it.subtitleFiles.filter(File::isFile).mapIndexed { index, _ ->
                        "/v1/media/${urlSegment(it.id)}/subtitle/$index"
                    },
                    artworkPath = it.posterFile?.takeIf(File::isFile)?.let {
                        "/v1/media/${urlSegment(it.id)}/artwork"
                    },
                )
            }
            .toList(),
    )
}

internal fun urlSegment(value: String): String =
    java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
