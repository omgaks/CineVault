package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File

/*
 * PlayerSubtitleDeleteFeedback.kt
 *
 * Owns the CineVault-styled subtitle deletion confirmation and Undo snackbar.
 * File deletion, restoration and active-track coordination remain in
 * VideoPlayerScreen; this file only renders feedback and reports actions.
 */

@Composable
internal fun BoxScope.PlayerSubtitleDeleteFeedback(
    pendingDeleteFile: File?,
    snackbarHostState: SnackbarHostState,
    bottomDockPadding: Dp,
    playButtonSize: Dp,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (File) -> Unit,
) {
    AnimatedVisibility(
        visible = pendingDeleteFile != null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        pendingDeleteFile?.let { file ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures { onDismissDelete() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = pendingDeleteFile != null,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 3 },
                        animationSpec = tween(180),
                    ) + fadeOut(tween(140)),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong)
                            .pointerInput(Unit) { detectTapGestures { } }
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Delete subtitle file?",
                            color = TextBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = file.name,
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Cancel",
                                color = TextBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable(onClick = onDismissDelete)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                            Text(
                                text = "Delete",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCore)
                                    .clickable { onConfirmDelete(file) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomDockPadding + playButtonSize + 26.dp),
    ) { data ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = data.visuals.message,
                color = TextBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    color = AmberCore,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { data.performAction() },
                )
            }
        }
    }
}
