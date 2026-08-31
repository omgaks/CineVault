package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.metadata.VideoWithMetadata
import com.sole.cinevault.ui.theme.*

@Composable
internal fun LibraryItemContextSheet(
    item: VideoWithMetadata?,
    isFavorite: Boolean,
    isHidden: Boolean,
    isInSecretFolder: Boolean,
    onDismiss: () -> Unit,
    onPlay: (VideoWithMetadata) -> Unit,
    onFavoriteToggle: (VideoWithMetadata) -> Unit,
    onSecretToggle: (VideoWithMetadata) -> Unit,
    onUnlockFolder: (VideoWithMetadata) -> Unit,
    onDelete: (VideoWithMetadata) -> Unit
) {
    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val selectedItem = item
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (selectedItem != null) {
                AnimatedVisibility(
                    visible = item != null,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 3 },
                        animationSpec = tween(180)
                    ) + fadeOut(tween(140))
                ) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
                            .clickable(enabled = false) { }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceDeep)
                            ) {
                                if (!selectedItem.posterUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = selectedItem.posterUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedItem.title,
                                color = TextBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GlassBorderBottom)
                        Spacer(modifier = Modifier.height(10.dp))

                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SheetIconButton(
                                icon = Icons.Rounded.PlayArrow,
                                tint = AmberCore,
                                contentDescription = "Play"
                            ) { onPlay(selectedItem) }

                            SheetIconButton(
                                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                tint = if (isFavorite) AmberCore else TextBright,
                                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
                            ) { onFavoriteToggle(selectedItem) }

                            SheetIconButton(
                                icon = if (isHidden) Icons.Filled.LockOpen else Icons.Rounded.Lock,
                                tint = TextBright,
                                contentDescription = if (isHidden) "Remove from Secret" else "Move to Secret"
                            ) { onSecretToggle(selectedItem) }

                            if (isInSecretFolder) {
                                SheetIconButton(
                                    icon = Icons.Filled.Folder,
                                    tint = TextBright,
                                    contentDescription = "Unlock Folder"
                                ) { onUnlockFolder(selectedItem) }
                            }

                            SheetIconButton(
                                icon = Icons.Rounded.Delete,
                                tint = Color(0xFFFF5252),
                                contentDescription = "Delete File"
                            ) { onDelete(selectedItem) }
                        }
                    }
                }
            }
        }
    }
}
