package com.sole.cinevault.network

enum class SharedLibraryScope { ENTIRE_LIBRARY, SELECTED_FOLDERS }

data class ShareLibrarySelection(
    val scope: SharedLibraryScope,
    val folderIds: Set<String> = emptySet(),
)

data class ShareableLibraryItem(
    val id: String,
    val path: String,
    val folderId: String?,
    val isVaultOrSecret: Boolean,
)

/**
 * Central privacy gate for Share My Library.
 * Vault/secret media is never shareable, even when Entire Library is selected.
 */
fun filterShareableLibrary(
    items: List<ShareableLibraryItem>,
    selection: ShareLibrarySelection,
): List<ShareableLibraryItem> =
    items.filter { item ->
        !item.isVaultOrSecret &&
            when (selection.scope) {
                SharedLibraryScope.ENTIRE_LIBRARY -> true
                SharedLibraryScope.SELECTED_FOLDERS ->
                    item.folderId != null && item.folderId in selection.folderIds
            }
    }

fun isSessionAuthorized(
    session: NearbyPairingSession?,
    remoteDeviceId: String,
    nowEpochMs: Long,
): Boolean =
    session != null &&
        session.remoteDeviceId == remoteDeviceId &&
        session.expiresAtEpochMs > nowEpochMs &&
        session.sessionToken.length >= 32
