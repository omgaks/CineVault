package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import java.io.File

@Composable
internal fun StudioManagerTab(
    videoPath: String,
    onSelectTrack: (SubtitleTrackChoice) -> Unit,
) {
    val context = LocalContext.current
    var cached by remember(videoPath) {
        mutableStateOf(
            OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)
        )
    }
    var pendingDelete by remember { mutableStateOf<CachedSubtitle?>(null) }
    var statusText by remember(videoPath) { mutableStateOf<String?>(null) }

    fun refresh() {
        cached =
            OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StudioSectionLabel("Downloaded Subtitles")
        Text(
            text =
                "Every downloaded language for this video. " +
                    "Files are deleted using their exact cached path.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))

        statusText?.let { message ->
            Text(
                text = message,
                color = AmberCore,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (cached.isEmpty()) {
            Text(
                text = "No downloaded subtitles for this video yet.",
                color = TextFaint,
                fontSize = 12.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cached.forEach { sub ->
                    val label =
                        SubtitleLanguageRegistry.displayName(sub.language)
                    val file = sub.uri.path?.let(::File)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SpaceDeep.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = TextBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sub.provider,
                                color = TextMuted,
                                fontSize = 10.sp,
                            )
                        }

                        if (file != null && file.exists()) {
                            Text(
                                text = "Use",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCore)
                                    .clickable {
                                        onSelectTrack(
                                            SubtitleTrackChoice.Downloaded(
                                                file = file,
                                                language = sub.language,
                                            )
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete $label subtitle",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    statusText = null
                                    pendingDelete = sub
                                },
                        )
                    }
                }
            }
        }

        pendingDelete?.let { toDelete ->
            val deleteLabel =
                SubtitleLanguageRegistry.displayName(toDelete.language)

            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpaceDeep.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        Color(0xFFFF5252).copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text =
                        "Delete $deleteLabel (${toDelete.provider}) subtitle?",
                    color = TextBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cancel",
                        color = TextBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GlassSurface)
                            .clickable { pendingDelete = null }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                    Text(
                        text = "Delete",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFF5252))
                            .clickable {
                                val deleted =
                                    OpenSubtitlesClient.deleteCachedSubtitle(
                                        context,
                                        toDelete,
                                    )
                                refresh()
                                statusText =
                                    if (deleted) {
                                        "$deleteLabel subtitle deleted"
                                    } else {
                                        "Couldn't delete $deleteLabel subtitle"
                                    }
                                pendingDelete = null
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}
