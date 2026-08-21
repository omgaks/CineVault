package com.sole.cinevault.metadata.artworkstudio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.metadata.ArtworkKind
import com.sole.cinevault.metadata.ArtworkOption
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ArtworkStudioDialog(
    items: List<VideoWithMetadata>,
    initialTool: ArtworkStudioTool = ArtworkStudioTool.OVERVIEW,
    onDismiss: () -> Unit,
    onApplied: (List<VideoWithMetadata>) -> Unit
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp
    var workingItems by remember(items) { mutableStateOf(items) }
    val current = workingItems.first()
    var tool by remember(initialTool) { mutableStateOf(initialTool) }
    var kind by remember { mutableStateOf(ArtworkKind.POSTER) }
    var source by remember { mutableStateOf(ArtworkStudioSource.ALL) }
    var gallery by remember(current.tmdbId) { mutableStateOf<ArtworkStudioGallery?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember(current.video.name) { mutableStateOf(current.video.name) }
    var matchType by remember(current.type) { mutableStateOf(if (current.type == "tv") "tv" else "movie") }
    var matchResults by remember { mutableStateOf<List<StudioMatchCandidate>>(emptyList()) }
    var matchLoading by remember { mutableStateOf(false) }
    var pendingLocalKind by remember { mutableStateOf(ArtworkKind.POSTER) }

    fun accept(result: ArtworkStudioResult<List<VideoWithMetadata>>, success: String) {
        when (result) {
            is ArtworkStudioResult.Success -> {
                workingItems = result.value
                onApplied(result.value)
                message = success
                reload++
            }
            is ArtworkStudioResult.Failure -> message = result.message
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            when (val imported = ArtworkLocalStore.importImage(context, current.video.path, pendingLocalKind, uri)) {
                is ArtworkStudioResult.Success -> accept(
                    ArtworkStudioRepository.applyChoice(context, workingItems, pendingLocalKind, imported.value),
                    "Local ${pendingLocalKind.name.lowercase()} applied"
                )
                is ArtworkStudioResult.Failure -> message = imported.message
            }
            busy = false
        }
    }

    LaunchedEffect(current.tmdbId, current.type, reload) {
        loading = true
        gallery = ArtworkStudioRepository.loadGallery(context, current)
        loading = false
    }

    BackHandler { onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (wide) 0.82f else 0.94f)
                .fillMaxHeight(if (wide) 0.90f else 0.88f)
                .widthIn(max = 920.dp)
                .clip(RoundedCornerShape(26.dp))
                .glassPanel(26.dp, SpaceMid.copy(alpha = 0.99f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Collections, contentDescription = null, tint = AmberCore, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Artwork Studio", color = TextBright, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text(current.title, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = AmberCore, strokeWidth = 2.dp)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = TextBright) }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(ArtworkStudioTool.entries) { entry ->
                    FilterChip(
                        selected = tool == entry,
                        onClick = { tool = entry; message = null },
                        label = { Text(entry.name.lowercase().replaceFirstChar { it.titlecase() }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberCore, selectedLabelColor = Color.Black)
                    )
                }
            }

            message?.let {
                Text(
                    text = it,
                    color = if (it.contains("applied", true) || it.contains("refreshed", true)) Color(0xFF83E6AE) else Color(0xFFFF8A80),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(9.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            when (tool) {
                ArtworkStudioTool.OVERVIEW -> StudioOverview(
                    item = current,
                    gallery = gallery,
                    loading = loading,
                    onBrowse = { kind = it; tool = ArtworkStudioTool.ARTWORK },
                    onRefresh = {
                        scope.launch { busy = true; accept(ArtworkStudioRepository.refresh(context, workingItems), "Artwork refreshed"); busy = false }
                    },
                    onReset = { resetKind ->
                        scope.launch { busy = true; accept(ArtworkStudioRepository.applyChoice(context, workingItems, resetKind, null), "Automatic artwork applied"); busy = false }
                    }
                )
                ArtworkStudioTool.MATCH -> StudioMatchPane(
                    query = query,
                    onQueryChanged = { query = it },
                    type = matchType,
                    onTypeChanged = { matchType = it },
                    loading = matchLoading,
                    results = matchResults,
                    onSearch = {
                        scope.launch {
                            matchLoading = true
                            when (val result = ArtworkStudioRepository.search(context, query, matchType)) {
                                is ArtworkStudioResult.Success -> {
                                    matchResults = result.value
                                    message = if (result.value.isEmpty()) "No matching titles found" else null
                                }
                                is ArtworkStudioResult.Failure -> message = result.message
                            }
                            matchLoading = false
                        }
                    },
                    onApply = { candidate ->
                        scope.launch {
                            busy = true
                            val result = ArtworkStudioRepository.applyMatch(context, workingItems, candidate)
                            accept(result, "Match and artwork updated")
                            if (result is ArtworkStudioResult.Success) tool = ArtworkStudioTool.OVERVIEW
                            busy = false
                        }
                    }
                )
                ArtworkStudioTool.ARTWORK -> StudioArtworkPane(
                    gallery = gallery,
                    loading = loading,
                    selectedKind = kind,
                    onKindChanged = { kind = it },
                    source = source,
                    onSourceChanged = { source = it },
                    currentUrl = if (kind == ArtworkKind.POSTER) current.posterUrl else current.backdropUrl,
                    onRetry = { reload++ },
                    onSelected = { option ->
                        scope.launch {
                            busy = true
                            accept(ArtworkStudioRepository.applyChoice(context, workingItems, kind, option.url), "${kind.name.lowercase().replaceFirstChar { it.titlecase() }} applied from ${option.source}")
                            busy = false
                        }
                    }
                )
                ArtworkStudioTool.LOCAL -> StudioLocalPane(
                    busy = busy,
                    onPick = { selectedKind -> pendingLocalKind = selectedKind; imagePicker.launch("image/*") },
                    onFrame = { selectedKind ->
                        scope.launch {
                            busy = true
                            when (val frame = ArtworkLocalStore.captureFrame(context, current.video.path, selectedKind)) {
                                is ArtworkStudioResult.Success -> accept(
                                    ArtworkStudioRepository.applyChoice(context, workingItems, selectedKind, frame.value),
                                    "Video frame applied"
                                )
                                is ArtworkStudioResult.Failure -> message = frame.message
                            }
                            busy = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StudioOverview(
    item: VideoWithMetadata,
    gallery: ArtworkStudioGallery?,
    loading: Boolean,
    onBrowse: (ArtworkKind) -> Unit,
    onRefresh: () -> Unit,
    onReset: (ArtworkKind) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StudioPreview("Poster • ${artworkSourceName(item.posterUrl)}", item.posterUrl, 0.68f, Modifier.weight(0.38f)) { onBrowse(ArtworkKind.POSTER) }
                StudioPreview("Backdrop • ${artworkSourceName(item.backdropUrl)}", item.backdropUrl, 1.65f, Modifier.weight(0.62f)) { onBrowse(ArtworkKind.BACKDROP) }
            }
        }
        item {
            Text("Current match", color = TextBright, fontWeight = FontWeight.Bold)
            Text("${item.title}  •  ${item.type.uppercase()}  •  TMDB ${item.tmdbId ?: "not matched"}", color = TextMuted, fontSize = 12.sp)
        }
        item {
            Text("Providers", color = TextBright, fontWeight = FontWeight.Bold)
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AmberCore)
            gallery?.reports?.forEach { report ->
                val color = when (report.status) {
                    ArtworkProviderStatus.READY -> Color(0xFF83E6AE)
                    ArtworkProviderStatus.ERROR -> Color(0xFFFF8A80)
                    else -> TextMuted
                }
                Text("${report.provider}: ${report.message}", color = color, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioAction("Refresh Sources", Icons.Rounded.Refresh, onRefresh)
                StudioAction("Reset Poster", Icons.Rounded.Restore, { onReset(ArtworkKind.POSTER) })
                StudioAction("Reset Backdrop", Icons.Rounded.Restore, { onReset(ArtworkKind.BACKDROP) })
            }
        }
    }
}

@Composable
private fun StudioPreview(label: String, url: String?, ratio: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(Color.Black).clickable(onClick = onClick)) {
            if (url != null) AsyncImage(model = url, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("Not set", color = TextMuted, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun StudioArtworkPane(
    gallery: ArtworkStudioGallery?, loading: Boolean,
    selectedKind: ArtworkKind, onKindChanged: (ArtworkKind) -> Unit,
    source: ArtworkStudioSource, onSourceChanged: (ArtworkStudioSource) -> Unit,
    currentUrl: String?, onRetry: () -> Unit, onSelected: (ArtworkOption) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(ArtworkKind.entries) { value ->
                FilterChip(selected = selectedKind == value, onClick = { onKindChanged(value) }, label = { Text(value.name.lowercase().replaceFirstChar { it.titlecase() }) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(ArtworkStudioSource.entries) { value ->
                FilterChip(selected = source == value, onClick = { onSourceChanged(value) }, label = { Text(value.label) })
            }
        }
        val options = gallery?.options.orEmpty().filter {
            it.kind == selectedKind && artworkSourceMatches(it.source, source)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AmberCore) }
            options.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No ${selectedKind.name.lowercase()} results", color = TextMuted)
                gallery?.reports?.forEach { Text("${it.provider}: ${it.message}", color = if (it.status == ArtworkProviderStatus.ERROR) Color(0xFFFF8A80) else TextFaint, fontSize = 11.sp) }
                Spacer(Modifier.height(10.dp)); StudioAction("Retry", Icons.Rounded.Refresh, onRetry)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (selectedKind == ArtworkKind.POSTER) 115.dp else 180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(options, key = { "${it.source}:${it.url}" }) { option ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(if (selectedKind == ArtworkKind.POSTER) 0.67f else 1.78f)
                            .clip(RoundedCornerShape(10.dp)).background(Color.Black)
                            .then(if (currentUrl == option.url) Modifier.border(2.dp, AmberCore, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onSelected(option) }
                    ) {
                        AsyncImage(option.url, option.source, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Text(option.source, color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .72f)).padding(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioMatchPane(
    query: String, onQueryChanged: (String) -> Unit,
    type: String, onTypeChanged: (String) -> Unit,
    loading: Boolean, results: List<StudioMatchCandidate>, onSearch: () -> Unit,
    onApply: (StudioMatchCandidate) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, onQueryChanged, label = { Text("Title and optional year") }, singleLine = true, modifier = Modifier.weight(1f))
            StudioAction("Search", Icons.Rounded.Search, onSearch)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == "movie", { onTypeChanged("movie") }, label = { Text("Movie") })
            FilterChip(type == "tv", { onTypeChanged("tv") }, label = { Text("TV Show") })
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AmberCore)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(results, key = { "${it.type}:${it.tmdbId}" }) { candidate ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f)).clickable { onApply(candidate) }.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(candidate.posterUrl, candidate.title, Modifier.size(52.dp, 76.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(candidate.title, color = TextBright, fontWeight = FontWeight.Bold)
                        Text("${candidate.year ?: "Year unknown"} • TMDB ${candidate.tmdbId}${candidate.rating?.let { " • %.1f".format(it) } ?: ""}", color = TextMuted, fontSize = 11.sp)
                        candidate.overview?.let { Text(it, color = TextFaint, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    Icon(Icons.Rounded.CheckCircle, "Apply match", tint = AmberCore)
                }
            }
        }
    }
}

@Composable
private fun StudioLocalPane(
    busy: Boolean,
    onPick: (ArtworkKind) -> Unit,
    onFrame: (ArtworkKind) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Local artwork", color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Imported images are copied into CineVault's private, non-backed-up artwork folder, so the selection survives restarts and storage permission changes without entering cloud backup.", color = TextMuted, fontSize = 12.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioAction("Import Poster", Icons.Rounded.Folder, { onPick(ArtworkKind.POSTER) })
                StudioAction("Import Backdrop", Icons.Rounded.Folder, { onPick(ArtworkKind.BACKDROP) })
            }
        }
        item { Text("Video frame", color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Captures a frame around one-third into a local or document-based video. SMB sources may not support direct frame extraction.", color = TextMuted, fontSize = 12.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioAction("Frame as Poster", Icons.Rounded.Image, { onFrame(ArtworkKind.POSTER) }, !busy)
                StudioAction("Frame as Backdrop", Icons.Rounded.Image, { onFrame(ArtworkKind.BACKDROP) }, !busy)
            }
        }
    }
}

@Composable
private fun StudioAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright),
        shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = AmberCore, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(label, fontSize = 11.sp)
    }
}
