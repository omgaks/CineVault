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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

@Composable
internal fun LibraryFolderSecretConfirmation(
    confirmation: Pair<String, List<String>>?,
    hiddenPaths: Set<String>,
    onDismiss: () -> Unit,
    onToggleSecret: (List<String>) -> Unit
) {
    confirmation?.let { (folderName, paths) ->
        val allHidden = paths.isNotEmpty() && paths.all { hiddenPaths.contains(it) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = confirmation != null,
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
                        .width(220.dp)
                        .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
                        .clickable(enabled = false) { }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpaceDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = AmberGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderName,
                                color = TextBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${paths.size} files",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = GlassBorderBottom)
                    Spacer(modifier = Modifier.height(10.dp))

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SheetIconButton(
                            icon = if (allHidden) Icons.Filled.LockOpen else Icons.Rounded.Lock,
                            tint = AmberCore,
                            contentDescription = if (allHidden) "Remove Folder from Secret" else "Move Folder to Secret"
                        ) {
                            onToggleSecret(paths)
                        }

                        SheetIconButton(
                            icon = Icons.Filled.Close,
                            tint = TextBright,
                            contentDescription = "Cancel"
                        ) {
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}
