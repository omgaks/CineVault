package com.sole.cinevault.metadata

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

private val ChooserAmber = Color(0xFFF5A623)
private val ChooserSurface = Color(0xFF16161F)
private val ChooserCard = Color(0xFF090A0E)

@Composable
fun ArtworkChooserDialog(
    tmdbId: Int,
    type: String,
    currentPosterUrl: String?,
    currentBackdropUrl: String?,
    onDismiss: () -> Unit,
    onSelected: (ArtworkKind, String?) -> Unit
) {
    val context = LocalContext.current
    var selectedKind by remember { mutableStateOf(ArtworkKind.POSTER) }
    var options by remember(tmdbId, type) { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var loading by remember(tmdbId, type) { mutableStateOf(true) }
    var error by remember(tmdbId, type) { mutableStateOf<String?>(null) }
    var retryRequest by remember(tmdbId, type) { mutableStateOf(0) }

    LaunchedEffect(tmdbId, type, retryRequest) {
        loading = true
        error = null
        try {
            options = loadArtworkOptions(context, tmdbId, type)
        } catch (e: Exception) {
            error = when {
                e.message?.contains("401") == true -> "TMDB rejected the configured credential (401)."
                e.message?.contains("404") == true -> "The saved TMDB match no longer exists (404). Try Fix Match."
                else -> e.message ?: "Artwork could not be loaded. Check the connection and try again."
            }
        } finally {
            loading = false
        }
    }

    val visibleOptions = options.filter { it.kind == selectedKind }.take(60)
    val currentUrl = if (selectedKind == ArtworkKind.POSTER) currentPosterUrl else currentBackdropUrl

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ChooserSurface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Choose Artwork",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedKind == ArtworkKind.POSTER,
                    onClick = { selectedKind = ArtworkKind.POSTER },
                    label = { Text("Posters") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ChooserAmber,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = selectedKind == ArtworkKind.BACKDROP,
                    onClick = { selectedKind = ArtworkKind.BACKDROP },
                    label = { Text("Backdrops") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ChooserAmber,
                        selectedLabelColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onSelected(selectedKind, null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ChooserAmber, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.size(5.dp))
                    Text("Automatic", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                loading -> Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ChooserAmber)
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = error.orEmpty(), color = Color(0xFFFF7777), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { retryRequest++ },
                        colors = ButtonDefaults.buttonColors(containerColor = ChooserAmber)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                visibleOptions.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(text = "No ${if (selectedKind == ArtworkKind.POSTER) "posters" else "backdrops"} available.", color = Color.Gray)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (selectedKind == ArtworkKind.POSTER) 3 else 2),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                ) {
                    items(visibleOptions, key = { "${it.kind}:${it.url}" }) { option ->
                        ArtworkOptionCard(
                            option = option,
                            selected = option.url == currentUrl,
                            onClick = { onSelected(selectedKind, option.url) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkOptionCard(
    option: ArtworkOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (option.kind == ArtworkKind.POSTER) 0.67f else 1.78f)
            .clip(RoundedCornerShape(10.dp))
            .background(ChooserCard)
            .then(if (selected) Modifier.border(2.dp, ChooserAmber, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = option.url,
            contentDescription = "${option.source} artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = option.source,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topEnd = 7.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
