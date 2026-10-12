package com.sole.cinevault.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import kotlinx.coroutines.launch

/**
 * "The rest of this franchise" on a film's detail page: release order, films you own
 * as normal posters, films you don't own as grey ghosts. Only appears when the
 * collection (or universe) has two or more films in total.
 *
 * Reuses the collection data layer, so it works from the cache when offline and
 * sends nothing but TMDB ids.
 */
@Composable
fun FranchiseStrip(
    item: VideoWithMetadata,
    libraryVideos: List<VideoWithMetadata>,
    isTelevision: Boolean,
    onOpenFilm: (VideoWithMetadata) -> Unit,
    onOpenCollection: (collectionId: Int, name: String) -> Unit,
    onOpenUniverse: (universeId: String) -> Unit,
    onSearchLibrary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val collectionId = item.collectionId ?: return
    val collectionName = item.collectionName ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val universe = remember(collectionName) { UniverseCatalog.forCollectionName(collectionName) }

    // Films of this franchise you own. The viewed film is always included.
    val owned = remember(libraryVideos, item, universe, collectionId) {
        val list = libraryVideos.filter {
            if (universe != null) universe.ownsCollectionName(it.collectionName) else it.collectionId == collectionId
        }
        if (list.any { it.video.path == item.video.path }) list else list + item
    }

    var load by remember(collectionId, universe) { mutableStateOf<CollectionLoad?>(null) }
    LaunchedEffect(collectionId, universe, owned.size) {
        load = if (universe != null) {
            val known = owned.mapNotNull { v ->
                val cid = v.collectionId
                val cname = v.collectionName
                if (cid != null && cname != null) UniverseCatalog.key(cname) to cid else null
            }.toMap()
            CollectionRepository.loadUniverse(context, universe, known)
        } else {
            CollectionRepository.load(context, collectionId)
        }
    }

    val story = universe?.story?.takeIf { it.isNotEmpty() }
    var orderName by rememberSaveable { mutableStateOf("release") }
    val today = remember { CollectionRepository.todayIso() }
    val plan = remember(load, owned, orderName, story) {
        CollectionPlanner.plan(load?.details, owned, today, if (orderName == "story") story else null) { false }
    }
    if (!FranchiseStripLogic.shouldShow(plan)) return

    val wanted by remember { CollectionRepository.wantlistIds(context) }.collectAsState(initial = emptySet())
    var sheetSlot by remember { mutableStateOf<CollectionSlot?>(null) }

    val currentIdx = FranchiseStripLogic.currentIndex(plan, item.tmdbId)
    val listState = rememberLazyListState()
    LaunchedEffect(currentIdx, plan.slots.size) {
        if (currentIdx > 0) listState.scrollToItem((currentIdx - 1).coerceAtLeast(0))
    }

    val title = universe?.name ?: collectionName
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).clickable {
                    if (universe != null) onOpenUniverse(universe.id) else onOpenCollection(collectionId, collectionName)
                }
            ) {
                Text(
                    "More from this franchise", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    FranchiseStripLogic.header(title, plan),
                    color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (story != null) {
                GlassPill(
                    text = if (orderName == "story") "Story order" else "Release order",
                    onClick = { orderName = if (orderName == "story") "release" else "story" }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(plan.slots, key = { it.part.tmdbId }) { slot ->
                SlotTile(
                    slot = slot,
                    watchedFraction = 0f,
                    wanted = slot.part.tmdbId in wanted,
                    isTelevision = isTelevision,
                    onOpenOwned = onOpenFilm,
                    onPlayOwned = onOpenFilm,
                    onOpenMissing = { sheetSlot = it },
                    modifier = Modifier.width(112.dp),
                    isCurrent = slot.part.tmdbId == item.tmdbId
                )
            }
        }
    }

    sheetSlot?.let { slot ->
        val isWanted = slot.part.tmdbId in wanted
        MissingFilmSheet(
            slot = slot,
            totalReleased = plan.releasedTotal.takeIf { plan.hasFullList },
            isWanted = isWanted,
            onToggleWanted = {
                scope.launch { CollectionRepository.setWanted(context, slot.part, collectionId, !isWanted) }
            },
            onSearchLibrary = {
                sheetSlot = null
                onSearchLibrary(slot.part.title)
            },
            onDismiss = { sheetSlot = null }
        )
    }
}
