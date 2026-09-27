package com.sole.cinevault.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.SpaceBlack
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

@Composable
fun RemoteLibraryScreen(
    source: NetworkSource,
    onBack: () -> Unit,
    onPlay: (NetworkVideo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(source.id) { mutableStateOf(RemoteLibraryState()) }
    val controller = remember(source.id, scope) { RemoteLibraryController(scope) { state = it } }

    LaunchedEffect(source.id) { controller.open(source) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    BackHandler(onBack = onBack)

    BoxWithConstraints(Modifier.fillMaxSize().background(SpaceBlack)) {
        val minCardWidth = when {
            maxWidth >= 1200.dp -> 190.dp
            maxWidth >= 720.dp -> 170.dp
            else -> 142.dp
        }
        Column(Modifier.fillMaxSize().padding(horizontal = if (maxWidth >= 720.dp) 32.dp else 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = AmberCore)
                }
                Column(Modifier.weight(1f)) {
                    Text(state.sourceName.ifBlank { source.displayName }, color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${source.type.friendlyType()} • Network Library", color = TextMuted, fontSize = 12.sp)
                }
                if (!state.loading) {
                    IconButton(onClick = controller::retry) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = AmberCore)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                state.loading -> RemoteLoading()
                state.errorMessage != null -> RemoteError(state.errorMessage!!, controller::retry)
                state.videos.isEmpty() -> RemoteEmpty(controller::retry)
                else -> {
                    Text("${state.videos.size} videos", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minCardWidth),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(state.videos, key = { it.path }) { video ->
                            RemoteVideoCard(video) { onPlay(video) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteVideoCard(video: NetworkVideo, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF15161A), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(8.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .background(AmberCore.copy(alpha = .08f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!video.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.posterUrl,
                    contentDescription = video.name,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Movie, null, tint = AmberCore, modifier = Modifier.size(42.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(50)).padding(7.dp)
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = AmberCore, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(video.name, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        video.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = TextMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun RemoteLoading() =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AmberCore)
            Spacer(Modifier.height(12.dp))
            Text("Opening your network library…", color = TextMuted)
        }
    }

@Composable private fun RemoteError(message: String, retry: () -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.WifiOff, null, tint = AmberCore, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = TextBright)
            Spacer(Modifier.height(12.dp))
            Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = AmberCore, contentColor = Color.Black)) {
                Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Try Again")
            }
        }
    }

@Composable private fun RemoteEmpty(refresh: () -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = AmberCore, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text("No playable videos found.", color = TextBright)
            TextButton(onClick = refresh) { Text("Refresh", color = AmberCore) }
        }
    }

internal fun NetworkType.friendlyType(): String = when (this) {
    NetworkType.SMB -> "SMB"
    NetworkType.WEBDAV -> "WebDAV"
    NetworkType.JELLYFIN -> "Jellyfin"
    NetworkType.EMBY -> "Emby"
    NetworkType.DLNA -> "DLNA"
    NetworkType.SFTP -> "SFTP"
    NetworkType.HTTP_DIRECTORY -> "Web"
    NetworkType.M3U -> "M3U"
    NetworkType.CINEVAULT_GATEWAY -> "CineVault Nearby"
}
