package com.sole.cinevault.metadata.artworkstudio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.TextBright

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkStudioPill(
    onOpen: (ArtworkStudioTool) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(GlassSurface)
                .border(1.dp, AmberCore.copy(alpha = 0.72f), RoundedCornerShape(40.dp))
                .combinedClickable(
                    onClick = { onOpen(ArtworkStudioTool.OVERVIEW) },
                    onLongClick = { expanded = true }
                )
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = AmberCore, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Artwork Studio", color = TextBright, fontSize = 12.5.sp)
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Artwork shortcuts", tint = AmberCore, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudioShortcut("Open Studio", Icons.Filled.VideoLibrary) { expanded = false; onOpen(ArtworkStudioTool.OVERVIEW) }
            StudioShortcut("Fix Match", Icons.Rounded.Search) { expanded = false; onOpen(ArtworkStudioTool.MATCH) }
            StudioShortcut("Choose Artwork", Icons.Rounded.Collections) { expanded = false; onOpen(ArtworkStudioTool.ARTWORK) }
            StudioShortcut("Refresh / Automatic", Icons.Rounded.Refresh) { expanded = false; onOpen(ArtworkStudioTool.OVERVIEW) }
            StudioShortcut("Local / Video Frame", Icons.Rounded.Folder) { expanded = false; onOpen(ArtworkStudioTool.LOCAL) }
        }
    }
}

@Composable
private fun StudioShortcut(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = AmberCore) },
        onClick = onClick
    )
}
