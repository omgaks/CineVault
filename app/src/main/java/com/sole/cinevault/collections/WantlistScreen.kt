package com.sole.cinevault.collections

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.VideoWithMetadata
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceFaint
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import kotlinx.coroutines.launch

private val GoodGreen = Color(0xFF83E6AE)

/**
 * Films you tapped "Add to wantlist" on, in one place. Owned ones are marked,
 * so the list tells you what is still worth hunting for.
 */
@Composable
fun WantlistScreen(
    videos: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onOpenCollection: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by remember { CollectionRepository.wantlist(context) }.collectAsState(initial = emptyList())
    val ownedIds = remember(videos) { videos.filter { it.type == "movie" }.mapNotNull { it.tmdbId }.toSet() }
    val rows = remember(stored, ownedIds) {
        WantlistPlanner.plan(
            stored.map { WantItem(it.tmdbId, it.title, it.posterPath, it.releaseDate, it.collectionId, it.addedAtMs) },
            ownedIds,
            CollectionRepository.todayIso()
        )
    }
    val ownedRows = rows.filter { it.status == WantStatus.OWNED }

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Wantlist", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(WantlistPlanner.summary(rows), color = TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Open any film you don't own yet on a collection page and tap Add to wantlist. It will be waiting here.",
                        color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            if (ownedRows.isNotEmpty()) {
                item {
                    Text(
                        "CLEAR THE ${ownedRows.size} YOU NOW OWN",
                        color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            scope.launch { ownedRows.forEach { CollectionRepository.removeWanted(context, it.film.tmdbId) } }
                        }.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
            items(rows, key = { it.film.tmdbId }) { row ->
                val statusColor = when (row.status) {
                    WantStatus.AVAILABLE -> AmberCore
                    WantStatus.UPCOMING -> TextMuted
                    WantStatus.OWNED -> GoodGreen
                }
                Row(
                    Modifier.fillMaxWidth().glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceFaint)
                        .then(if (row.film.collectionId != null) Modifier.clickable { onOpenCollection(row.film.collectionId) } else Modifier)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(52.dp).height(78.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                        row.film.posterPath?.let {
                            AsyncImage(
                                model = "https://image.tmdb.org/t/p/w185$it",
                                contentDescription = row.film.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.film.title, color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(row.label, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (row.film.collectionId != null) {
                            Text("Tap to open its collection", color = TextFaint, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "REMOVE", color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            scope.launch { CollectionRepository.removeWanted(context, row.film.tmdbId) }
                        }.padding(8.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
