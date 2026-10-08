package com.sole.cinevault.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceFaint
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import kotlinx.coroutines.launch

private val DECADES = listOf(1950, 1960, 1970, 1980, 1990, 2000, 2010, 2020)
private val RATINGS = listOf(6.0, 7.0, 8.0)

/**
 * "My collections": hand-picked sets (Halloween, Date night) and smart sets
 * (a saved filter over your library, like 1980s horror rated 7+). Everything
 * stays on this device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCollectionsScreen(
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onOpen: (Long, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by remember { CustomCollections.collections(context) }.collectAsState(initial = emptyList())
    val members by remember { CustomCollections.members(context) }.collectAsState(initial = emptyMap())

    var creating by remember { mutableStateOf(false) }
    var pickingFor by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<CustomCollectionEntity?>(null) }

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("My collections", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Hand-picked or smart. Only on this device.", color = TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "NEW COLLECTION",
                    color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { creating = true }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
                if (collections.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Make a collection to group films your way. A smart collection fills itself, for example 1980s Horror rated 7 or more.",
                        color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
            items(collections, key = { it.id }) { c ->
                val rule = SmartRule.decode(c.rule)
                val count = CustomCollections.resolve(c, members[c.id].orEmpty(), videos).size
                Column(
                    Modifier.fillMaxWidth().glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceFaint)
                        .clickable { onOpen(c.id, c.name) }.padding(14.dp)
                ) {
                    Text(c.name, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (rule != null) "Smart · ${rule.describe()}" else "Hand-picked") + "  ·  $count ${if (count == 1) "film" else "films"}",
                        color = AmberCore, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (rule == null) Text("EDIT FILMS", color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { pickingFor = c.id })
                        Text("DELETE", color = TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { deleting = c })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (creating) {
        NewCollectionDialog(
            videos = videos,
            onDismiss = { creating = false },
            onCreate = { name, rule ->
                creating = false
                scope.launch {
                    val id = CustomCollections.create(context, name, rule, emptySet())
                    if (rule == null) pickingFor = id
                }
            }
        )
    }

    pickingFor?.let { id ->
        val entity = collections.firstOrNull { it.id == id }
        PickFilmsDialog(
            title = entity?.name ?: "Pick films",
            videos = videos,
            initial = members[id].orEmpty(),
            onDismiss = { pickingFor = null },
            onDone = { picked ->
                pickingFor = null
                scope.launch { CustomCollections.setMembers(context, id, picked) }
            }
        )
    }

    deleting?.let { c ->
        Dialog(onDismissRequest = { deleting = null }) {
            Column(Modifier.clip(RoundedCornerShape(22.dp)).background(SpaceMid).padding(20.dp)) {
                Text("Delete \"${c.name}\"?", color = TextBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Only the collection is removed. Your films are not touched.", color = TextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.clickable { deleting = null })
                    Text("DELETE", color = AmberCore, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.clickable {
                        deleting = null
                        scope.launch { CustomCollections.delete(context, c.id) }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCollectionDialog(
    videos: List<VideoWithMetadata>,
    onDismiss: () -> Unit,
    onCreate: (String, SmartRule?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf(setOf<String>()) }
    var decade by remember { mutableStateOf<Int?>(null) }
    var minRating by remember { mutableStateOf<Double?>(null) }
    val allGenres = remember(videos) {
        videos.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(14).map { it.key }
    }
    val rule = SmartRule(genres, decade, minRating)
    val canCreate = name.isNotBlank() && (!smart || !rule.isEmpty)
    val config = LocalConfiguration.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(if (config.screenWidthDp > 700) 0.6f else 0.92f)
                .clip(RoundedCornerShape(24.dp)).background(SpaceMid).padding(20.dp)
        ) {
            Text("New collection", color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextBright, unfocusedTextColor = TextBright,
                    focusedBorderColor = AmberCore, unfocusedBorderColor = TextFaint,
                    focusedLabelColor = AmberCore, unfocusedLabelColor = TextMuted, cursorColor = AmberCore
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !smart, onClick = { smart = false }, label = { Text("Hand-picked") }, colors = chipColors())
                FilterChip(selected = smart, onClick = { smart = true }, label = { Text("Smart") }, colors = chipColors())
            }
            if (smart) {
                Spacer(Modifier.height(10.dp))
                Text("Genre (any of)", color = TextMuted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allGenres.forEach { g ->
                        FilterChip(
                            selected = g in genres, onClick = { genres = if (g in genres) genres - g else genres + g },
                            label = { Text(g) }, colors = chipColors()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Decade", color = TextMuted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DECADES.forEach { d ->
                        FilterChip(selected = decade == d, onClick = { decade = if (decade == d) null else d }, label = { Text("${d}s") }, colors = chipColors())
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Minimum rating", color = TextMuted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RATINGS.forEach { r ->
                        FilterChip(selected = minRating == r, onClick = { minRating = if (minRating == r) null else r }, label = { Text("${r.toInt()}+") }, colors = chipColors())
                    }
                }
                Spacer(Modifier.height(8.dp))
                val preview = if (rule.isEmpty) 0 else videos.count { v ->
                    (v.type == "movie" || v.type == "tv") &&
                        rule.matches(v.genres, if (v.type == "movie") v.subtitle.take(4).toIntOrNull() else null, v.rating, v.director)
                }
                Text(
                    if (rule.isEmpty) "Choose at least one filter." else "$preview ${if (preview == 1) "film matches" else "films match"} right now.",
                    color = AmberCore, fontSize = 12.sp
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text("You will pick the films next.", color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDismiss))
                Text(
                    "CREATE", color = if (canCreate) AmberCore else TextFaint, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = canCreate) { onCreate(name, if (smart) rule else null) }
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AmberCore, selectedLabelColor = Color.Black
)

@Composable
private fun PickFilmsDialog(
    title: String,
    videos: List<VideoWithMetadata>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val movies = remember(videos) { videos.filter { it.type == "movie" }.sortedBy { it.title.lowercase() } }
    val shown = remember(movies, query) { if (query.isBlank()) movies else movies.filter { it.title.contains(query, ignoreCase = true) } }
    val config = LocalConfiguration.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(if (config.screenWidthDp > 700) 0.6f else 0.94f).fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(24.dp)).background(SpaceMid).padding(16.dp)
        ) {
            Text(title, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${picked.size} selected", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search your films") }, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextBright, unfocusedTextColor = TextBright,
                    focusedBorderColor = AmberCore, unfocusedBorderColor = TextFaint,
                    focusedLabelColor = AmberCore, unfocusedLabelColor = TextMuted, cursorColor = AmberCore
                )
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.video.path }) { v ->
                    val checked = v.video.path in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (checked) picked - v.video.path else picked + v.video.path }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked, onCheckedChange = { picked = if (checked) picked - v.video.path else picked + v.video.path },
                            colors = CheckboxDefaults.colors(checkedColor = AmberCore, checkmarkColor = Color.Black, uncheckedColor = TextFaint)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = TextBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            v.subtitle.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }?.let {
                                Text(it, color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDismiss))
                Text("DONE", color = AmberCore, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.clickable { onDone(picked) })
            }
        }
    }
}
