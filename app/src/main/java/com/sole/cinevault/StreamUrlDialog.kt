package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.tvmode.TelevisionModeDetector
import com.sole.cinevault.tvmode.TvFocusableSlot

@Composable
fun StreamUrlDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    // Phase 16 of TV support: the URL field is the whole point of this
    // dialog, so it claims initial focus directly (it's already natively
    // focusable — no TvFocusableSlot needed) so the on-screen keyboard opens
    // immediately on TV. Left/Right for Cancel/PLAY is scoped to just their
    // Row, not the whole Column, so it never interferes with cursor
    // movement while the field has focus — same split used since phase 10.
    val context = LocalContext.current
    val isTelevision = remember { TelevisionModeDetector.isRunningOnTelevision(context) }
    val focusManager = LocalFocusManager.current
    val urlFieldFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(Unit) {
            urlFieldFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(22.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B1B1B),
                            Color(0xFF090909)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Text(
                text = "🌐 Stream URL",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Paste MP4, M3U8 or WEBM link.",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = {
                    Text("https://example.com/video.mp4")
                },
                modifier = Modifier.fillMaxWidth().focusRequester(urlFieldFocusRequester),
                singleLine = false,
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD36A),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFFFD36A)
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            val onPlayClick = {
                val cleanUrl = url.trim()
                if (cleanUrl.startsWith("http://", ignoreCase = true) ||
                    cleanUrl.startsWith("https://", ignoreCase = true)
                ) {
                    onPlayUrl(cleanUrl)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.then(
                    if (isTelevision) {
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    focusManager.moveFocus(FocusDirection.Left)
                                    true
                                }
                                Key.DirectionRight -> {
                                    focusManager.moveFocus(FocusDirection.Right)
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        Modifier
                    }
                )
            ) {
                TvFocusableSlot(isTelevision = isTelevision, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), onActivate = onDismiss) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                }

                TvFocusableSlot(isTelevision = isTelevision, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), onActivate = onPlayClick) {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD36A),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("PLAY", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
