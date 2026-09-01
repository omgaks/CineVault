package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.DuplicateGroup
import com.sole.cinevault.library.formatFileSize
import com.sole.cinevault.ui.theme.*
import java.io.File

internal fun LazyGridScope.LocalLibraryDuplicatesSection(
    selectedCategory: String,
    duplicateGroups: List<DuplicateGroup>,
    onDeleteCopy: (VideoWithMetadata) -> Unit
) {
    if (selectedCategory != "Duplicates") return

    if (duplicateGroups.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No duplicates found.",
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        }
        return
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                text = "Duplicates (${duplicateGroups.size})",
                color = TextBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grouped by matching file size — review before deleting anything.",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }

    duplicateGroups.forEach { group ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 14.dp)
                    .padding(14.dp)
            ) {
                Text(
                    text = "${group.videos.size} copies · ${group.videos.firstOrNull()?.title ?: "Unknown"}",
                    color = AmberGlow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(10.dp))

                group.videos.forEach { copy ->
                    val copyFile = File(copy.video.path)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = copyFile.name,
                                color = TextBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = copyFile.parent ?: copy.video.path,
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text(
                                text = formatFileSize(copyFile.length()),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Delete",
                            color = Color(0xFFFF8080),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A0A0A))
                                .clickable { onDeleteCopy(copy) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
