package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ── Studio tabs ───────────────────────────────────────────────────────────
// FIX (UI redesign round): was TRACK/SYNC/STYLE/POSITION/ADVANCED (5 tabs,
// with Position and Style artificially split even though the approved
// mockup's own Style panel example shows Text Size and Vertical Offset
// together in one place) — now 5 tabs matching the 6-tile Tools Grid minus
// "Find" (which isn't a Studio tab at all; it closes Studio and opens the
// separate Search sheet instead, same as it always has). Position's
// controls now live inside Appearance; Dual Subtitles — previously buried
// at the bottom of the old catch-all Advanced tab — is its own tool now.
// MANAGE added this round — lists every language already downloaded for
// THIS video (via OpenSubtitlesClient.listCachedSubtitlesForVideo) with a
// delete action per language, addressing "no way to manage downloaded
// subtitles" directly instead of leaving cached files to just accumulate
// silently.

@Composable
internal fun StudioManagerTab(
    videoPath: String,
    onSelectTrack: (SubtitleTrackChoice) -> Unit
) {
    val context = LocalContext.current
    var cached by remember(videoPath) { mutableStateOf(OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)) }
    var pendingDelete by remember { mutableStateOf<CachedSubtitle?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Downloaded Subtitles")
        Text(
            text = "Every language already downloaded for this video, with which provider it came from.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (cached.isEmpty()) {
            Text(text = "No downloaded subtitles for this video yet.", color = TextFaint, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cached.forEach { sub ->
                    val label = SubtitleLanguageRegistry.displayName(sub.language)
                    val file = sub.uri.path?.let { File(it) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SpaceDeep.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = sub.provider, color = TextMuted, fontSize = 10.sp)
                        }
                        if (file != null) {
                            Text(
                                text = "Use", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCore)
                                    .clickable { onSelectTrack(SubtitleTrackChoice.Downloaded(file = file, language = sub.language)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete $label subtitle",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp).clickable { pendingDelete = sub }
                        )
                    }
                }
            }
        }

        pendingDelete?.let { toDelete ->
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpaceDeep.copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = "Delete ${SubtitleLanguageRegistry.displayName(toDelete.language)} (${toDelete.provider}) subtitle?", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cancel", color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassSurface).clickable { pendingDelete = null }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                    Text(
                        text = "Delete", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF5252)).clickable {
                            OpenSubtitlesClient.deleteCachedSubtitle(context, toDelete)
                            cached = OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)
                            pendingDelete = null
                        }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
