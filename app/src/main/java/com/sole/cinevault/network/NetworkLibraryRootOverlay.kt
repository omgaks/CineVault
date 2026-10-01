package com.sole.cinevault.network

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Root-level overlay used by CineVaultApp.
 *
 * Keeping the placement outside NetworkHub means it survives normal app
 * navigation while CineVaultNearbyRuntime keeps the authenticated session.
 */
@Composable
fun BoxScope.NetworkLibraryRootOverlay(
    connection: CineVaultRemoteConnection?,
    isPlayerActive: Boolean,
    isRemoteLibraryActive: Boolean,
    onOpenLibrary: (CineVaultRemoteConnection) -> Unit,
) {
    NetworkLibraryFloatingPill(
        connection = connection,
        visible = connection != null && !isPlayerActive && !isRemoteLibraryActive,
        onOpenLibrary = onOpenLibrary,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 14.dp, end = 14.dp),
    )
}
