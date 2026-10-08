package com.sole.cinevault.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.getWatchedPercent
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import com.sole.cinevault.tvmode.TvFocusableSlot
import com.sole.cinevault.ui.responsive.CineAdaptiveTokens
import com.sole.cinevault.ui.responsive.cineAdaptiveTokens
import com.sole.cinevault.ui.responsive.rememberCineWindowSizeInfo
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassPanel
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a collection page's film list comes from. */
sealed interface CollectionSource {
    /** One TMDB collection. */
    data class Tmdb(val collectionId: Int) : CollectionSource

    /** Several TMDB collections merged into one franchise page, with an optional story order. */
    data class UniverseSource(val universe: Universe, val knownIds: Map<String, Int>) : CollectionSource

    /** A curated group with no TMDB list (the MCU keyword group): the user's own films only. */
    data object OwnedOnly : CollectionSource
}

/**
 * Collections V2 page: your films in release (or story) order, the ones you don't own as
 * ghost posters, progress, a "next up", a release timeline and a marathon. Adapts to the window
 * through the project-wide CineWindowSizeInfo / CineAdaptiveTokens (see CollectionLayout.kt).
 *
 * [ownedItems] must already exclude Secret / restricted-folder videos.
 */
@Composable
fun CollectionPageV2(
    source: CollectionSource,
    title: String,
    ownedItems: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlay: (VideoWithMetadata, List<VideoWithMetadata>) -> Unit,
    onSearchLibrary: (String) -> Unit,
    isTelevision: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = rememberCineWindowSizeInfo()
    val tokens = cineAdaptiveTokens(info)
    val spec = collectionLayoutFor(info)

    var load by remember(source) { mutableStateOf<CollectionLoad?>(null) }
    var loaded by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        load = when (source) {
            is CollectionSource.Tmdb -> CollectionRepository.load(context, source.collectionId)
            is CollectionSource.UniverseSource -> CollectionRepository.loadUniverse(context, source.universe, source.knownIds)
            CollectionSource.OwnedOnly -> null
        }
        loaded = true
    }

    // Watch progress is read off the main thread, once per set of owned files.
    val ownedKey = ownedItems.map { it.video.path }
    var watched by remember(ownedKey) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    LaunchedEffect(ownedKey) {
        watched = withContext(Dispatchers.IO) {
            ownedItems.associate { it.video.path to getWatchedPercent(context, it) }
        }
    }

    val wanted by remember { CollectionRepository.wantlistIds(context) }.collectAsState(initial = emptySet())
    val today = remember { CollectionRepository.todayIso() }

    val story = (source as? CollectionSource.UniverseSource)?.universe?.story?.takeIf { it.isNotEmpty() }
    val isFinished: (VideoWithMetadata) -> Boolean = { (watched[it.video.path] ?: 0f) >= 0.9f }
    val releasePlan = remember(load, ownedItems, watched) {
        CollectionPlanner.plan(load?.details, ownedItems, today, null, isFinished)
    }
    val storyPlan = remember(load, ownedItems, watched, story) {
        story?.let { CollectionPlanner.plan(load?.details, ownedItems, today, it, isFinished) }
    }

    // rememberSaveable with plain strings so a rotation keeps the chosen mode.
    var orderName by rememberSaveable { mutableStateOf("release") }
    var viewName by rememberSaveable { mutableStateOf("posters") }
    val orderMode = if (orderName == "story" && storyPlan != null) OrderMode.STORY else OrderMode.RELEASE
    val viewMode = if (viewName == "timeline") ViewMode.TIMELINE else ViewMode.POSTERS
    val plan = if (orderMode == OrderMode.STORY && storyPlan != null) storyPlan else releasePlan
    val timelineRows = remember(releasePlan) { CollectionPlanner.timelineRows(releasePlan) }

    val fetchEnabled = remember { loadMetadataFetchEnabled(context) }
    val statusText = CollectionPlanner.statusLabel(
        load?.fetchedAtMs, load?.isStale == true, System.currentTimeMillis(), fetchEnabled
    )
    val notice: String? = when {
        !loaded || plan.hasFullList || source is CollectionSource.OwnedOnly -> null
        !fetchEnabled -> "Online lookups are off, so only what's in your library is shown."
        else -> "Couldn't load the full collection, so only what's in your library is shown."
    }
    val heroUrl = tmdbImageUrl(load?.details?.backdropPath, "w1280")
        ?: ownedItems.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl
    val wantlistCollectionId = (source as? CollectionSource.Tmdb)?.collectionId

    var sheetSlot by remember { mutableStateOf<CollectionSlot?>(null) }

    // Every play from this page is a marathon over the list as currently ordered: the player
    // shows its "Up next" countdown between films instead of jumping straight to the next one.
    val startPlay: (VideoWithMetadata) -> Unit = { v ->
        MarathonSession.start(plan.orderedOwned.map { it.video.path })
        onPlay(v, plan.orderedOwned)
    }

    val model = PageModel(
        title = load?.details?.name?.takeIf { source is CollectionSource.Tmdb } ?: title,
        plan = plan,
        timelineRows = timelineRows,
        heroUrl = heroUrl,
        statusText = statusText,
        watched = watched,
        wanted = wanted,
        notice = notice,
        isTelevision = isTelevision,
        tokens = tokens,
        spec = spec,
        orderMode = orderMode,
        viewMode = viewMode,
        hasStory = story != null && storyPlan != null,
        onOrder = { orderName = if (it == OrderMode.STORY) "story" else "release" },
        onView = { viewName = if (it == ViewMode.TIMELINE) "timeline" else "posters" },
        onOpenOwned = onItemClick,
        onPlay = startPlay,
        onMarathon = { (plan.nextUp ?: plan.orderedOwned.firstOrNull())?.let(startPlay) },
        onOpenMissing = { sheetSlot = it }
    )

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        if (spec.twoPane) TwoPane(model) else SinglePane(model)

        TvFocusableSlot(
            isTelevision = isTelevision,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            shape = CircleShape,
            onActivate = onBack
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(GlassSurfaceStrong).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright, modifier = Modifier.size(22.dp))
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
                scope.launch { CollectionRepository.setWanted(context, slot.part, wantlistCollectionId, !isWanted) }
            },
            onSearchLibrary = {
                sheetSlot = null
                onSearchLibrary(slot.part.title)
            },
            onDismiss = { sheetSlot = null }
        )
    }
}

private class PageModel(
    val title: String,
    val plan: CollectionPlan,
    val timelineRows: List<CollectionPlanner.TimelineRow>,
    val heroUrl: String?,
    val statusText: String,
    val watched: Map<String, Float>,
    val wanted: Set<Int>,
    val notice: String?,
    val isTelevision: Boolean,
    val tokens: CineAdaptiveTokens,
    val spec: CollectionLayoutSpec,
    val orderMode: OrderMode,
    val viewMode: ViewMode,
    val hasStory: Boolean,
    val onOrder: (OrderMode) -> Unit,
    val onView: (ViewMode) -> Unit,
    val onOpenOwned: (VideoWithMetadata) -> Unit,
    val onPlay: (VideoWithMetadata) -> Unit,
    val onMarathon: () -> Unit,
    val onOpenMissing: (CollectionSlot) -> Unit
)

@Composable
private fun SinglePane(m: PageModel) {
    val gridState = rememberLazyGridState()
    val heroPx = with(LocalDensity.current) { m.spec.heroHeight.toPx() }
    val heroAlpha by remember(heroPx) {
        derivedStateOf {
            when {
                heroPx <= 0f -> 0f
                gridState.firstVisibleItemIndex > 0 -> 0f
                else -> (1f - gridState.firstVisibleItemScrollOffset / heroPx).coerceIn(0f, 1f)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        CollectionHero(
            m.heroUrl,
            Modifier.fillMaxWidth().height(m.spec.heroHeight).graphicsLayer { alpha = heroAlpha }
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(m.spec.minPosterWidth),
            contentPadding = PaddingValues(start = m.tokens.screenMargin, end = m.tokens.screenMargin, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(m.tokens.componentGap),
            verticalArrangement = Arrangement.spacedBy(m.tokens.componentGap),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(m.tokens.componentGap)) {
                    Spacer(Modifier.height((m.spec.heroHeight - 140.dp).coerceAtLeast(72.dp)))
                    CollectionSummary(m)
                    LegendRow(Modifier.padding(top = 4.dp))
                }
            }
            contentItems(m)
            item(span = { GridItemSpan(maxLineSpan) }) { Attribution(m) }
        }
        StatusChip(m.statusText, Modifier.align(Alignment.TopEnd).padding(14.dp))
    }
}

@Composable
private fun TwoPane(m: PageModel) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(m.spec.leftPaneWidth).fillMaxHeight()) {
            if (m.spec.shortHero) {
                Box(Modifier.fillMaxSize().background(SpaceDeep))
            } else {
                CollectionHero(m.heroUrl, Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = m.tokens.screenMargin)
                    .padding(top = 62.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(m.tokens.componentGap)
            ) {
                Spacer(Modifier.height(if (m.spec.shortHero) 8.dp else (m.spec.heroHeight - 62.dp).coerceAtLeast(0.dp)))
                CollectionSummary(m)
                Attribution(m)
            }
            StatusChip(m.statusText, Modifier.align(Alignment.TopEnd).padding(14.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(m.spec.minPosterWidth),
            contentPadding = PaddingValues(m.tokens.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(m.tokens.componentGap),
            verticalArrangement = Arrangement.spacedBy(m.tokens.componentGap),
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val heading = when {
                        m.viewMode == ViewMode.TIMELINE -> "Timeline"
                        m.orderMode == OrderMode.STORY -> "In story order"
                        else -> "In order"
                    }
                    Text(heading, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    LegendRow()
                }
            }
            contentItems(m)
        }
    }
}

private fun LazyGridScope.contentItems(m: PageModel) {
    if (m.viewMode == ViewMode.TIMELINE) {
        timelineItems(m.timelineRows, m.watched, m.wanted, m.tokens.componentGap, m.isTelevision, m.onOpenOwned, m.onOpenMissing)
    } else {
        slotItems(m)
    }
}

private fun LazyGridScope.slotItems(m: PageModel) {
    items(m.plan.slots, key = { it.part.tmdbId }) { slot ->
        SlotTile(
            slot = slot,
            watchedFraction = slot.owned?.let { m.watched[it.video.path] } ?: 0f,
            wanted = slot.part.tmdbId in m.wanted,
            isTelevision = m.isTelevision,
            onOpenOwned = m.onOpenOwned,
            onPlayOwned = m.onPlay,
            onOpenMissing = m.onOpenMissing
        )
    }
}

@Composable
private fun CollectionSummary(m: PageModel) {
    val plan = m.plan
    Column(verticalArrangement = Arrangement.spacedBy(m.tokens.componentGap)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(m.title, color = TextBright, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(6.dp))
                Text(CollectionPlanner.subtitle(plan), color = TextMuted, fontSize = 13.sp)
                if (plan.isComplete) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "COMPLETE", color = androidx.compose.ui.graphics.Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberGlow).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (plan.hasFullList && plan.releasedTotal > 0) {
                ProgressRing(plan.progress, plan.ownedCount, plan.releasedTotal, 68.dp)
            }
        }
        m.notice?.let { text ->
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Text(text, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(12.dp))
            }
        }
        NextUpCard(m)
        if (plan.hasFullList) {
            ModeRow(
                hasStory = m.hasStory,
                order = m.orderMode,
                view = m.viewMode,
                isTelevision = m.isTelevision,
                onOrder = m.onOrder,
                onView = m.onView
            )
        }
        if (plan.orderedOwned.size >= 2) {
            TvFocusableSlot(isTelevision = m.isTelevision, shape = RoundedCornerShape(50), onActivate = m.onMarathon) {
                GlassPill("Marathon", m.onMarathon, icon = Icons.Rounded.PlayArrow)
            }
        }
    }
}

@Composable
private fun NextUpCard(m: PageModel) {
    val plan = m.plan
    val next = plan.nextUp
    if (next == null) {
        if (plan.ownedCount > 0) {
            Text("You've watched everything you own here.", color = TextMuted, fontSize = 12.sp)
        }
        return
    }
    val slot = plan.slots.firstOrNull { it.owned === next }
    val pct = m.watched[next.video.path] ?: 0f
    val of = if (plan.hasFullList) " of ${plan.releasedTotal}" else ""
    val line = listOfNotNull(
        slot?.number?.let { "Film $it$of" },
        if (pct > 0f) "${(pct * 100).toInt()}% watched" else null
    ).joinToString(" · ").ifBlank { "In your library" }
    val poster = next.posterUrl?.takeIf { it.isNotBlank() } ?: tmdbImageUrl(slot?.part?.posterPath)
    val play = { m.onPlay(next) }

    Row(
        Modifier.fillMaxWidth()
            .glassPanel(20.dp, GlassSurfaceStrong)
            .border(1.dp, AmberGlow.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(width = 52.dp, height = 78.dp).clip(RoundedCornerShape(10.dp)).background(SpaceDeep)) {
            if (poster != null) {
                AsyncImage(model = poster, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text("NEXT UP", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(next.title, color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, color = TextMuted, fontSize = 12.sp)
        }
        TvFocusableSlot(isTelevision = m.isTelevision, shape = RoundedCornerShape(50), onActivate = play) {
            AmberPill(if (pct > 0f) "Resume" else "Play", play)
        }
    }
}

@Composable
private fun Attribution(m: PageModel) {
    if (!m.plan.hasFullList) return
    Text(
        "Film data from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. Missing films are information only; nothing is downloaded.",
        color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.glassPanel(50.dp, GlassSurfaceStrong).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, color = TextMuted, fontSize = 12.sp)
    }
}
