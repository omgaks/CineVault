package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

// Shared by any screen in this package that needs a persistent, retryable
// error banner instead of a toast that flashes by. Not exposed as a toast
// replacement everywhere — only for consequential failures (scan, delete,
// network share) where a Retry action is meaningful.
data class ErrorBannerState(val message: String, val onRetry: (() -> Unit)? = null)

@Composable
fun ErrorBanner(state: ErrorBannerState, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2A0A0A))
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = state.message, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (state.onRetry != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "RETRY", color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { state.onRetry.invoke(); onDismiss() }
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Close, contentDescription = "Dismiss", tint = TextMuted,
            modifier = Modifier.size(18.dp).clickable { onDismiss() }
        )
    }
}

// Shared empty-state block — icon + heading + subtext + optional action
// slot, used wherever a screen has nothing to show (empty library, no
// search results, etc.) instead of a single line of plain text.
@Composable
fun EmptyStateBlock(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GlassSurfaceStrong)
                .border(1.dp, AmberGlow.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = subtitle, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp, modifier = Modifier.widthIn(max = 260.dp))
        Spacer(modifier = Modifier.height(18.dp))
        actions()
    }
}

