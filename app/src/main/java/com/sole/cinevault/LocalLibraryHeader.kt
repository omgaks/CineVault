package com.sole.cinevault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.library.*
import com.sole.cinevault.ui.theme.*

@Composable
internal fun LocalLibraryHeader(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    sortOption: LibrarySortOption,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    isGridMode: Boolean,
    onToggleGridMode: () -> Unit,
    isScanning: Boolean,
    scanStatus: String,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    context: Context
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items = categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGlow.copy(alpha = 0.18f),
                        selectedLabelColor = AmberCore,
                        containerColor = Color.Transparent,
                        labelColor = TextMuted
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            if (scanStatus.isNotBlank()) {
                Text(
                    text = scanStatus,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 10.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box {
                LibraryToolIconButton(
                    icon = Icons.Filled.SwapVert,
                    tint = Color(0xFF56CCF2),
                    contentDescription = "Sort by: ${sortOption.label}",
                    label = "Sort",
                    onClick = { onSortMenuExpandedChange(true) }
                )

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuExpandedChange(false) },
                    modifier = Modifier.background(SpaceMid)
                ) {
                    LibrarySortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = if (sortOption == option) AmberCore else TextBright,
                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortSelected(option)
                                onSortMenuExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            LibraryToolIconButton(
                icon = if (isGridMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                tint = Color(0xFFBB86FC),
                contentDescription = if (isGridMode) "Switch to List" else "Switch to Grid",
                label = if (isGridMode) "List" else "Grid",
                onClick = onToggleGridMode
            )

            Spacer(modifier = Modifier.width(10.dp))

            LibraryToolIconButton(
                icon = Icons.Filled.Refresh,
                tint = Color(0xFF6FCF97),
                contentDescription = "Refresh / Clear Cache",
                label = "Refresh",
                enabled = !isScanning,
                onClick = onRefresh
            )

            Spacer(modifier = Modifier.width(10.dp))

            LibraryToolIconButton(
                icon = Icons.Filled.TrackChanges,
                tint = AmberCore,
                contentDescription = "Scan Device Videos",
                label = "Scan",
                enabled = !isScanning,
                onClick = onScan
            )
        }

        val lastScanCache by produceState<CachedLibrary?>(initialValue = null, context) {
            value = loadLibraryCache(context)
        }

        lastScanCache?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last Scan: " + java.text.SimpleDateFormat(
                    "hh:mm a",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(it.timestamp)),
                color = TextFaint,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
