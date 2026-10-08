package com.sole.cinevault.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberDeep
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted

internal enum class OrderMode { RELEASE, STORY }
internal enum class ViewMode { POSTERS, TIMELINE }

@Composable
internal fun SelectablePill(
    text: String,
    selected: Boolean,
    isTelevision: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    TvFocusableSlot(isTelevision = isTelevision, shape = shape, onActivate = onClick) {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(if (selected) AmberGlow else GlassSurfaceStrong)
                .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.18f), shape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.Black else TextBright,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
            )
        }
    }
}

/**
 * Release/Story order (only for universes that have a real story order, and only in poster
 * view) and Posters/Timeline. Story order is never offered where it would equal release order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeRow(
    hasStory: Boolean,
    order: OrderMode,
    view: ViewMode,
    isTelevision: Boolean,
    onOrder: (OrderMode) -> Unit,
    onView: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasStory && view == ViewMode.POSTERS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectablePill("Release order", order == OrderMode.RELEASE, isTelevision) { onOrder(OrderMode.RELEASE) }
                SelectablePill("Story order", order == OrderMode.STORY, isTelevision) { onOrder(OrderMode.STORY) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectablePill("Posters", view == ViewMode.POSTERS, isTelevision) { onView(ViewMode.POSTERS) }
            SelectablePill("Timeline", view == ViewMode.TIMELINE, isTelevision) { onView(ViewMode.TIMELINE) }
        }
        if (view == ViewMode.TIMELINE) {
            Text("Timeline follows release dates.", color = TextMuted, fontSize = 11.sp)
        }
    }
}

/** The release timeline: a spine with one node per film, years on the left. Full-width rows. */
internal fun LazyGridScope.timelineItems(
    rows: List<CollectionPlanner.TimelineRow>,
    watched: Map<String, Float>,
    wanted: Set<Int>,
    rowGap: Dp,
    isTelevision: Boolean,
    onOpenOwned: (VideoWithMetadata) -> Unit,
    onOpenMissing: (CollectionSlot) -> Unit
) {
    items(rows, key = { "tl-${it.slot.part.tmdbId}" }, span = { GridItemSpan(maxLineSpan) }) { row ->
        TimelineRowItem(
            row = row,
            watchedFraction = row.slot.owned?.let { watched[it.video.path] } ?: 0f,
            wanted = row.slot.part.tmdbId in wanted,
            rowGap = rowGap,
            isTelevision = isTelevision,
            onOpenOwned = onOpenOwned,
            onOpenMissing = onOpenMissing
        )
    }
}

@Composable
private fun TimelineRowItem(
    row: CollectionPlanner.TimelineRow,
    watchedFraction: Float,
    wanted: Boolean,
    rowGap: Dp,
    isTelevision: Boolean,
    onOpenOwned: (VideoWithMetadata) -> Unit,
    onOpenMissing: (CollectionSlot) -> Unit
) {
    val slot = row.slot
    val owned = slot.owned
    val onActivate: () -> Unit = { if (owned != null) onOpenOwned(owned) else onOpenMissing(slot) }
    val nodeColor = when (slot.status) {
        SlotStatus.OWNED -> AmberGlow
        SlotStatus.MISSING -> if (slot.isGap) AmberCore else AmberDeep
        SlotStatus.UPCOMING -> TextFaint
    }
    val isOwned = slot.status == SlotStatus.OWNED
    val posterUrl = owned?.posterUrl?.takeIf { it.isNotBlank() } ?: tmdbImageUrl(slot.part.posterPath, "w185")

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(52.dp).fillMaxHeight().padding(top = 14.dp)) {
            row.yearLabel?.let {
                Text(it, color = if (isOwned) AmberCore else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        // The spine + node. Drawn past the row's bottom edge by the grid gap so it reads as one line.
        Box(
            Modifier.width(24.dp).fillMaxHeight().drawBehind {
                val cx = size.width / 2f
                val cy = 22.dp.toPx()
                val top = if (row.isFirst) cy else 0f
                val bottom = if (row.isLast) cy else size.height + rowGap.toPx()
                drawLine(Color.White.copy(alpha = 0.16f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 2.dp.toPx())
                if (isOwned) {
                    drawCircle(AmberGlow.copy(alpha = 0.30f), 11.dp.toPx(), Offset(cx, cy))
                    drawCircle(AmberGlow, 6.dp.toPx(), Offset(cx, cy))
                } else {
                    drawCircle(SpaceBlack, 6.dp.toPx(), Offset(cx, cy))
                    drawCircle(nodeColor, 6.dp.toPx(), Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                }
            }
        )
        TvFocusableSlot(
            isTelevision = isTelevision,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            onActivate = onActivate
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onActivate).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val thumbShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.size(width = 48.dp, height = 72.dp).clip(thumbShape).background(SpaceDeep)
                        .then(if (isOwned) Modifier.border(2.dp, AmberGlow, thumbShape) else Modifier.dashedBorder(nodeColor, 10.dp))
                ) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            alpha = if (isOwned) 1f else 0.45f,
                            colorFilter = if (isOwned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        slot.part.title,
                        color = if (isOwned) TextBright else TextMuted,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(slotStateText(slot, watchedFraction), color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (wanted) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = "On your wantlist", tint = AmberCore, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
