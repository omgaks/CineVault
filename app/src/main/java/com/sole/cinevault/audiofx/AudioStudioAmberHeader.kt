package com.sole.cinevault.audiofx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Amber Lens identity header for Audio Studio. Presentation only:
 * the enabled state is supplied by the existing AudioFxController.
 */
@Composable
internal fun AudioStudioAmberHeader(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val amber = NightCityEnvironment.signatureAmber
    val glass = NightCityEnvironment.glassBase
    Column(
        modifier = modifier.fillMaxWidth()
            .background(glass.copy(alpha = 0.84f), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CINEVAULT  /  AUDIO",
                color = amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.7.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (enabled) "● FX ACTIVE" else "○ FX OFF",
                color = if (enabled) amber else Color(0xFFA8A6A0),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Audio Studio",
            color = Color(0xFFF5F3EE),
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Shape the sound. Watch the city respond.",
            color = Color(0xFFA8A6A0),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        // Two compact rows prevent badge overflow in narrow player popups.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                listOf("AMBER LENS", "LIVE SPECTRUM"),
                listOf("ANIME NIGHT CITY"),
            ).forEach { labels ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        Text(
                            text = label,
                            color = if (label == "AMBER LENS") amber else Color(0xFFB9B7C1),
                            fontSize = 9.sp,
                            modifier = Modifier
                                .background(Color(0xFF1D2130), RoundedCornerShape(18.dp))
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
