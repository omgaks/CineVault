package com.sole.cinevault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.RestrictedFolder
import com.sole.cinevault.ui.theme.*

internal data class SecretFolderGroup(
    val folder: RestrictedFolder,
    val items: List<VideoWithMetadata>
)

internal fun LazyGridScope.LocalLibrarySecretLockedSection(
    selectedCategory: String,
    secretUnlocked: Boolean,
    onUnlock: () -> Unit
) {
    if (selectedCategory != "Secret" || secretUnlocked) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒 Secret folder is locked",
                    color = TextBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onUnlock,
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGlow.copy(alpha = 0.90f),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Unlock Secret Folder",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

internal fun LazyGridScope.LocalLibrarySecretFoldersShelf(
    selectedCategory: String,
    secretUnlocked: Boolean,
    groups: List<SecretFolderGroup>,
    onRestrictedFolderClick: (RestrictedFolder) -> Unit,
    onRestrictedFolderLongClick: (String, List<String>) -> Unit
) {
    if (
        selectedCategory != "Secret" ||
        !secretUnlocked ||
        groups.isEmpty()
    ) {
        return
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                text = "Secret Folders",
                color = TextBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(
                    items = groups,
                    key = { "secretfolder:${it.folder.id}" }
                ) { group ->
                    val thumbnailSourcePath = group.folder.lastPlayedVideoPath
                        ?.takeIf { path ->
                            group.items.any { it.video.path == path }
                        }
                        ?: group.items.firstOrNull()?.video?.path

                    RestrictedFolderShelfCard(
                        title = group.folder.displayName,
                        count = group.items.size,
                        thumbnailVideoPath = thumbnailSourcePath,
                        onClick = {
                            onRestrictedFolderClick(group.folder)
                        },
                        onLongClick = {
                            onRestrictedFolderLongClick(
                                group.folder.displayName,
                                group.items.map { it.video.path }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
