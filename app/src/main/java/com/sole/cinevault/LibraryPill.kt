package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

private data class PillTool(val panel: LibraryPanel, val icon: ImageVector, val gel: Color)

private fun gelFor(panel: LibraryPanel): Color = when (panel) {
    LibraryPanel.Search -> GelGold
    LibraryPanel.Category -> GelSky
    LibraryPanel.Genre -> GelViolet
    LibraryPanel.Sort -> GelMint
    LibraryPanel.View -> GelCoral
    LibraryPanel.Refresh -> GelRose
    LibraryPanel.Scan -> GelGold
}

/**
 * The floating glass pill at the top of Library. Each icon opens its own
 * card under the pill. Icons are 48dp so they are easy to hit; on a very
 * narrow window the pill scrolls sideways instead of squeezing them.
 */
@Composable
internal fun LibraryPill(
    openPanel: LibraryPanel?,
    viewMode: LibraryViewMode,
    isScanning: Boolean,
    onToggle: (LibraryPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewIcon = when (viewMode) {
        LibraryViewMode.Grid -> Icons.Filled.GridView
        LibraryViewMode.Details -> Icons.Filled.ViewAgenda
        LibraryViewMode.Compact -> Icons.Filled.ViewList
    }
    val tools = listOf(
        PillTool(LibraryPanel.Search, Icons.Filled.Search, gelFor(LibraryPanel.Search)),
        PillTool(LibraryPanel.Category, Icons.Filled.Category, gelFor(LibraryPanel.Category)),
        PillTool(LibraryPanel.Genre, Icons.Filled.TheaterComedy, gelFor(LibraryPanel.Genre)),
        PillTool(LibraryPanel.Sort, Icons.Filled.SwapVert, gelFor(LibraryPanel.Sort)),
        PillTool(LibraryPanel.View, viewIcon, gelFor(LibraryPanel.View)),
        PillTool(LibraryPanel.Refresh, Icons.Filled.Refresh, gelFor(LibraryPanel.Refresh)),
        PillTool(LibraryPanel.Scan, Icons.Filled.TrackChanges, gelFor(LibraryPanel.Scan))
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xE010131C))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .horizontalScroll(rememberScrollState())
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { tool ->
            val active = openPanel == tool.panel
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (active) tool.gel.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (active) tool.gel.copy(alpha = 0.55f) else Color.Transparent, CircleShape)
                    .clickable(role = Role.Button) { onToggle(tool.panel) }
                    .semantics { contentDescription = tool.panel.title },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = if (active) tool.gel else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
                if (tool.panel == LibraryPanel.Scan && isScanning) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GelGold)
                    )
                }
            }
        }
    }
}

/** The card that opens under the pill. The close button is always top right. */
@Composable
internal fun LibraryPanelCard(
    panel: LibraryPanel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gel = gelFor(panel)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cineCard(radius = 26.dp, gel = gel)
            .background(SpaceMid.copy(alpha = 0.96f))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = panel.title,
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Title,
                modifier = Modifier.weight(1f)
            )
            CineCloseButton(onClick = onClose)
        }
        Box(modifier = Modifier.padding(end = 12.dp)) { content() }
    }
}

@Composable
internal fun LibraryChoiceChips(
    labels: List<String>,
    selected: String?,
    gel: Color,
    onPick: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            CineChip(text = label, selected = label == selected, onClick = { onPick(label) }, gel = gel)
        }
    }
}

@Composable
internal fun LibraryCategoryPanel(selected: String, onPick: (String) -> Unit) {
    LibraryChoiceChips(LIBRARY_CATEGORIES, selected, GelSky, onPick)
}

@Composable
internal fun LibraryGenrePanel(genres: List<String>, onPick: (String) -> Unit) {
    if (genres.isEmpty()) {
        Text(
            text = "Genres appear here once your films have online details.",
            color = TextMuted,
            fontSize = CineType.Label,
            lineHeight = 19.sp
        )
    } else {
        LibraryChoiceChips(genres, null, GelViolet, onPick)
    }
}

@Composable
internal fun LibrarySortPanel(selected: LibrarySortOption, onPick: (LibrarySortOption) -> Unit) {
    LibraryChoiceChips(
        labels = LibrarySortOption.values().map { it.label },
        selected = selected.label,
        gel = GelMint,
        onPick = { label -> LibrarySortOption.values().firstOrNull { it.label == label }?.let(onPick) }
    )
}

@Composable
internal fun LibraryViewPanel(selected: LibraryViewMode, onPick: (LibraryViewMode) -> Unit) {
    LibraryChoiceChips(
        labels = LibraryViewMode.values().map { it.label },
        selected = selected.label,
        gel = GelCoral,
        onPick = { label -> LibraryViewMode.values().firstOrNull { it.label == label }?.let(onPick) }
    )
}

@Composable
internal fun LibraryRefreshPanel(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Refresh clears the saved library list and scans again. Your files, favourites and watch history are not touched.",
            color = TextMuted,
            fontSize = CineType.Label,
            lineHeight = 19.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CineButton(text = "Cancel", onClick = onCancel, style = CineButtonStyle.Secondary)
            CineButton(text = "Refresh", onClick = onConfirm, style = CineButtonStyle.Danger)
        }
    }
}

@Composable
internal fun LibraryScanPanel(
    isScanning: Boolean,
    status: String,
    upToDate: Boolean,
    lastScan: String?,
    onScan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val message = when {
            upToDate -> "Up to date"
            status.isNotBlank() -> status
            isScanning -> "Scanning..."
            else -> "Look for new films on this device, your network shares and your selected folders."
        }
        Text(
            text = message,
            color = if (upToDate) GelMint else TextMuted,
            fontSize = CineType.Label,
            fontWeight = if (upToDate) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 19.sp
        )
        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AmberCore,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "You can close this. The scan keeps going.",
                color = TextFaint,
                fontSize = CineType.Caption
            )
        } else {
            CineButton(text = "Scan now", onClick = onScan)
        }
        if (lastScan != null) {
            Text(text = "Last scan: $lastScan", color = TextFaint, fontSize = CineType.Caption)
        }
        Spacer(modifier = Modifier.height(0.dp))
    }
}
