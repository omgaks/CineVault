package com.sole.cinevault

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.sole.cinevault.collections.CollectionRepository
import com.sole.cinevault.library.LocalArtworkFolders
import com.sole.cinevault.library.LocalArtworkImporter
import com.sole.cinevault.library.saveLibraryCache
import com.sole.cinevault.metadata.DoubtfulMatch
import com.sole.cinevault.metadata.MatchHealth
import com.sole.cinevault.metadata.RematchDialog
import com.sole.cinevault.metadata.findMatchProblems
import com.sole.cinevault.metadata.loadMetadataFetchEnabled
import com.sole.cinevault.metadata.rematchAutomatically
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceFaint
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AccentArt = Color(0xFFE8C77A)
private val AccentMatch = Color(0xFF8FD9A8)
private const val MAX_ROWS = 25

/**
 * Library tools: bring in artwork you already keep beside your films, and
 * review matches that look wrong. Nothing here deletes a file or overwrites
 * artwork you picked yourself.
 */
@Composable
fun LibraryToolsScreen(
    videos: List<VideoWithMetadata>,
    onVideosUpdated: (List<VideoWithMetadata>) -> Unit,
    onBack: () -> Unit,
    onOpenWantlist: () -> Unit = {},
    onOpenMyCollections: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var folders by remember { mutableStateOf(LocalArtworkFolders.load(context)) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var artStatus by remember { mutableStateOf<String?>(null) }
    var matchStatus by remember { mutableStateOf<String?>(null) }
    var fixing by remember { mutableStateOf<VideoWithMetadata?>(null) }
    val fetchOn = remember { loadMetadataFetchEnabled(context) }

    val problems by produceState(initialValue = emptyList<DoubtfulMatch>(), videos) {
        value = withContext(Dispatchers.Default) { findMatchProblems(context, videos) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            LocalArtworkFolders.add(context, uri.toString())
            folders = LocalArtworkFolders.load(context)
            artStatus = null
        }
    }

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Library tools", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Artwork and matches", color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Collections you keep ─────────────────────────────────────
            val wantedCount = remember { CollectionRepository.wantlistIds(context) }.collectAsState(initial = emptySet()).value.size
            GlassSectionCard(
                title = "Your collections",
                subtitle = "Films you want, and sets you make yourself.",
                icon = Icons.Rounded.Collections,
                accent = AmberGlow
            ) {
                GlassActionRow(
                    icon = Icons.Rounded.Collections, iconTint = AmberGlow,
                    title = "Wantlist",
                    subtitle = if (wantedCount == 0) "Films you want to find or watch for" else "$wantedCount ${if (wantedCount == 1) "film" else "films"} you want",
                    action = "OPEN", onClick = onOpenWantlist
                )
                Spacer(Modifier.height(8.dp))
                GlassActionRow(
                    icon = Icons.Rounded.Collections, iconTint = AmberGlow,
                    title = "My collections",
                    subtitle = "Hand-picked sets and smart ones like 1980s Horror",
                    action = "OPEN", onClick = onOpenMyCollections
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Local artwork ────────────────────────────────────────────
            GlassSectionCard(
                title = "Local artwork",
                subtitle = "Use poster, fanart and NFO files kept beside your films.",
                icon = Icons.Rounded.Collections,
                accent = AccentArt
            ) {
                Text(
                    "Add the folder your films live in. CineVault reads only that folder, copies any artwork it finds " +
                        "into its own storage, and never overwrites artwork you picked yourself. " +
                        "Names understood: Film-poster.jpg, Film-fanart.jpg, poster.jpg, folder.jpg, fanart.jpg, and Film.nfo (for the exact TMDB match).",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                folders.forEach { uriString ->
                    val label = remember(uriString) {
                        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Folder"
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).glassPanel(cornerRadius = 16.dp, fill = GlassSurfaceFaint).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextBright, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "REMOVE", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable {
                                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                LocalArtworkFolders.remove(context, uriString)
                                folders = LocalArtworkFolders.load(context)
                            }.padding(6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassActionRow(
                    icon = Icons.Rounded.Collections, iconTint = AccentArt,
                    title = "Add a folder", subtitle = "Pick where your films and their artwork are", action = "CHOOSE"
                ) { if (!working) picker.launch(null) }
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    GlassActionRow(
                        icon = Icons.Rounded.Build, iconTint = AccentArt,
                        title = "Import artwork now", subtitle = "Look beside each film for artwork and NFO files", action = if (working) "WORKING" else "RUN"
                    ) {
                        if (!working) scope.launch {
                            working = true; artStatus = null; progress = 0f
                            val (list, report) = LocalArtworkImporter.import(context, videos) { done, total ->
                                progress = if (total <= 0) null else done.toFloat() / total
                            }
                            onVideosUpdated(list)
                            artStatus = report.summary()
                            progress = null; working = false
                        }
                    }
                }
                artStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, color = AccentMatch, fontSize = 12.sp) }
                progress?.let { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = AmberCore) }
            }

            Spacer(Modifier.height(18.dp))

            // ── Match review ─────────────────────────────────────────────
            val doubtful = problems.count { it.audit.health == MatchHealth.DOUBTFUL }
            val unmatched = problems.count { it.audit.health == MatchHealth.UNMATCHED }
            GlassSectionCard(
                title = "Match review",
                subtitle = "Films whose match does not look right.",
                icon = Icons.Rounded.Build,
                accent = AccentMatch
            ) {
                Text(
                    if (problems.isEmpty()) "Every match looks consistent with its file name."
                    else "$doubtful look doubtful, $unmatched have no match yet.",
                    color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This is a quick check of title and year against the file name, so a few correct matches can show up here. " +
                        "Fix opens the search so you can pick the right film.",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                problems.take(MAX_ROWS).forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).glassPanel(cornerRadius = 16.dp, fill = GlassSurfaceFaint).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.item.video.name, color = TextBright, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.audit.health == MatchHealth.UNMATCHED) "No match yet"
                                else "Matched as ${p.item.title}${p.item.subtitle.take(4).takeIf { it.length == 4 && p.item.type == "movie" }?.let { " ($it)" } ?: ""}  ·  ${p.audit.reason}",
                                color = if (p.audit.health == MatchHealth.UNMATCHED) TextFaint else AmberGlow,
                                fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (p.item.type == "movie") {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "FIX MATCH", color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable { fixing = p.item }.padding(horizontal = 10.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
                if (problems.size > MAX_ROWS) {
                    Text("…and ${problems.size - MAX_ROWS} more.", color = TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (problems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    GlassActionRow(
                        icon = Icons.Rounded.Build, iconTint = AccentMatch,
                        title = "Search again automatically",
                        subtitle = if (fetchOn) "Re-runs the automatic match for everything listed. Your picked artwork is kept." else "Turn metadata fetching on in Settings first",
                        action = if (working) "WORKING" else "RUN"
                    ) {
                        if (!working && fetchOn) scope.launch {
                            working = true; matchStatus = null; progress = 0f
                            val before = problems.size
                            val list = rematchAutomatically(context, videos, problems.map { it.item }) { done, total ->
                                progress = if (total <= 0) null else done.toFloat() / total
                            }
                            onVideosUpdated(list)
                            val after = findMatchProblems(context, list).size
                            matchStatus = if (after < before) "Fixed ${before - after}. $after still need a look." else "No change. Use FIX to pick the right film by hand."
                            progress = null; working = false
                        }
                    }
                }
                matchStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, color = AccentMatch, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    fixing?.let { target ->
        RematchDialog(
            currentItem = target,
            onDismiss = { fixing = null },
            onApplied = { updated ->
                fixing = null
                val merged = videos.map { if (it.video.path == updated.video.path) updated else it }
                onVideosUpdated(merged)
                scope.launch { saveLibraryCache(context, merged) }
            }
        )
    }
}
