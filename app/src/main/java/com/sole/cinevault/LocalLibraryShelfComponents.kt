package com.sole.cinevault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

private fun folderIconFor(displayName: String): ImageVector {
    val lower = displayName.lowercase()
    return when {
        lower.contains("tiktok") -> Icons.Filled.MusicNote
        lower.contains("instagram") || lower.contains("insta") -> Icons.Filled.PhotoCamera
        lower.contains("whatsapp") -> Icons.Filled.Chat
        lower.contains("camera") || lower.contains("dcim") -> Icons.Filled.CameraAlt
        else -> Icons.Filled.Folder
    }
}

@Composable
internal fun LibraryToolIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(GlassSurfaceStrong)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = if (enabled) 0.30f else 0.10f), Color.Transparent), radius = 80f))
                .border(1.2.dp, tint.copy(alpha = if (enabled) 0.60f else 0.20f), CircleShape)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = if (enabled) tint else tint.copy(alpha = 0.35f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SheetIconButton(icon: ImageVector, tint: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.30f), Color.Transparent), radius = 90f))
            .border(1.2.dp, tint.copy(alpha = 0.55f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun CollectionShelfCard(title: String, backdropUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SpaceMid)
            .clickable { onClick() }
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.62f)))
            )
        )
        Text(
            text = title,
            color = TextBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RestrictedFolderShelfCard(
    title: String,
    count: Int,
    thumbnailVideoPath: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // Same portrait card shape as the TV Shows row (145dp wide, 210dp
    // poster, title + count below), now with an amber-glass border and a
    // slightly zoomed/cropped thumbnail so it reads as a designed card
    // instead of a bare rectangle — previously this had none of the glow/
    // border treatment every other poster card on this screen already uses.
    // Long-press now opens the same context sheet movies/TV posters get
    // (Play / Favorite / Secret / Hide folder / Delete), using the folder's
    // representative item — previously long-press did nothing at all here.
    Column(
        modifier = Modifier
            .width(145.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SpaceMid)
                .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.55f), AmberDeep.copy(alpha = 0.25f))), RoundedCornerShape(14.dp))
        ) {
            // No posterUrl on purpose — restricted-folder items never go
            // through TMDB enrichment, so there's no online artwork.
            // PosterBox falls back to generating a thumbnail directly from a
            // frame of the video file itself when posterUrl is null.
            // Slight scale-up (1.10x) crops in a touch tighter for a more
            // "designed" zoomed look instead of the raw untouched frame.
            PosterBox(
                posterUrl = null,
                videoPath = thumbnailVideoPath,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f)))
                )
            )
            // Folder-type badge — generic icon only (see folderIconFor), not
            // an actual brand logo.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = folderIconFor(title), contentDescription = null, tint = AmberCore, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, color = TextBright, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(text = "$count file${if (count != 1) "s" else ""}", color = TextMuted, fontSize = 12.sp)
    }
}
