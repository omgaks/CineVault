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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreamUrlDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

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
                modifier = Modifier.fillMaxWidth(),
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val cleanUrl = url.trim()
                        if (cleanUrl.startsWith("http://", ignoreCase = true) ||
                            cleanUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            onPlayUrl(cleanUrl)
                        }
                    },
                    modifier = Modifier.weight(1f),
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
