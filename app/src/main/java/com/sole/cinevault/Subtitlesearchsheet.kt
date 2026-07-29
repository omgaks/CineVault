package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import kotlin.math.roundToInt

// ── Subtitle Download Search sheet ──────────────────────────────────────
// Two states in one composable: a manual-search header (query/season/
// episode, editable — essential per spec when auto-derived metadata is
// wrong) always visible at top, and a scrollable list of ranked result
// cards below it. `isSearching` drives a small inline spinner rather than
// blocking the whole sheet, so the person can adjust the query and re-fire
// a search without the panel flickering closed.
@Composable
fun SubtitleSearchSheet(
    initialQuery: String,
    initialSeason: String,
    initialEpisode: String,
    results: List<SubtitleSearchResult>,
    isSearching: Boolean,
    statusText: String,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    onSearch: (query: String, season: String, episode: String) -> Unit,
    onDownloadAndApply: (SubtitleSearchResult) -> Unit,
    onDownloadOnly: (SubtitleSearchResult) -> Unit,
    onWebsiteFallback: () -> Unit,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {}
) {
    var query by remember { mutableStateOf(initialQuery) }
    var season by remember { mutableStateOf(initialSeason) }
    var episode by remember { mutableStateOf(initialEpisode) }
    var showManualFields by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Same proven pattern as the Studio: bounded drag via long-press on
    // the header, root touch-containment so nothing leaks through to the
    // video underneath, and a catch-all activity ping so this popup's own
    // auto-close timer (see VideoPlayerScreen.kt) resets on real use
    // instead of ticking down regardless of what you're doing in here.
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxOffsetXPx = with(density) { ((containerWidth - popupWidth) / 2).coerceAtLeast(0.dp).toPx() }
    val maxOffsetYPx = with(density) { ((containerHeight - popupMaxHeight) / 2).coerceAtLeast(0.dp).toPx() }

    Column(
        modifier = Modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.98f))
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserInteraction()
                }
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxOffsetXPx, maxOffsetXPx)
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-maxOffsetYPx, maxOffsetYPx)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Download Subtitles", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconCircleSmall2(icon = Icons.Default.Close, onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextBright),
            placeholder = { Text("Movie or show title", fontSize = 12.sp, color = TextMuted) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Search, contentDescription = "Search", tint = AmberCore,
                    modifier = Modifier.size(16.dp).clickable { onSearch(query, season, episode) }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberCore.copy(alpha = 0.6f),
                unfocusedBorderColor = AmberCore.copy(alpha = 0.25f),
                focusedContainerColor = SpaceDeep.copy(alpha = 0.5f),
                unfocusedContainerColor = SpaceDeep.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (showManualFields) AmberGlow.copy(alpha = 0.16f) else SpaceDeep.copy(alpha = 0.6f))
                .border(1.dp, AmberCore.copy(alpha = if (showManualFields) 0.6f else 0.25f), RoundedCornerShape(10.dp))
                .clickable { showManualFields = !showManualFields }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (showManualFields) "▾ TV show: Season / Episode" else "▸ TV show? Set Season / Episode",
                color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }

        if (showManualFields) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = season, onValueChange = { season = it.filter { c -> c.isDigit() } },
                    singleLine = true, label = { Text("Season", fontSize = 9.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextBright),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberCore.copy(alpha = 0.6f), unfocusedBorderColor = AmberCore.copy(alpha = 0.25f))
                )
                OutlinedTextField(
                    value = episode, onValueChange = { episode = it.filter { c -> c.isDigit() } },
                    singleLine = true, label = { Text("Episode", fontSize = 9.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextBright),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberCore.copy(alpha = 0.6f), unfocusedBorderColor = AmberCore.copy(alpha = 0.25f))
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Search", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onSearch(query, season, episode) }.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(6.dp))

        if (isSearching) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AmberCore, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Searching…", color = TextMuted, fontSize = 11.sp)
            }
        } else if (results.isEmpty()) {
            Text(
                text = statusText.ifBlank { "No results yet — try Search" },
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Text(
                text = "Search website", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, AmberCore.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onWebsiteFallback() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        } else {
            // FIX (D2): previously one merged, scrollable list (SubDL
            // results sorted first per B6, but still visually mixed in
            // with everything else). Now split into two side-by-side
            // columns so each provider's results are immediately,
            // visually distinct — no need to scan a long single list to
            // tell which is which.
            val subDlResults = results.filter { it.provider == "SubDL" }
            val openSubsResults = results.filter { it.provider != "SubDL" }
            Row(modifier = Modifier.weight(1f, fill = false).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SubDL (${subDlResults.size})", color = Color(0xFFFFEE2A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (subDlResults.isEmpty()) {
                        Text(text = "No SubDL results", color = TextMuted, fontSize = 10.sp)
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            subDlResults.forEachIndexed { index, result ->
                                SubtitleResultCard(
                                    result = result,
                                    isBestMatch = index == 0,
                                    onDownloadAndApply = { onDownloadAndApply(result) },
                                    onDownloadOnly = { onDownloadOnly(result) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OpenSubtitles (${openSubsResults.size})", color = Color(0xFF56CCF2), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (openSubsResults.isEmpty()) {
                        Text(text = "No OpenSubtitles results", color = TextMuted, fontSize = 10.sp)
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            openSubsResults.forEachIndexed { index, result ->
                                SubtitleResultCard(
                                    result = result,
                                    isBestMatch = index == 0,
                                    onDownloadAndApply = { onDownloadAndApply(result) },
                                    onDownloadOnly = { onDownloadOnly(result) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleResultCard(
    result: SubtitleSearchResult,
    isBestMatch: Boolean,
    onDownloadAndApply: () -> Unit,
    onDownloadOnly: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SpaceDeep.copy(alpha = 0.65f))
                .border(
                    1.dp,
                    if (isBestMatch) Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f), AmberDeep.copy(alpha = 0.30f)))
                    else Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                    RoundedCornerShape(14.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = friendlyLanguageDisplay2(result.language),
                color = TextBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                // Room reserved on the right so the title never runs
                // under the corner badge below.
                modifier = Modifier.padding(end = 56.dp)
            )
            Text(
                text = result.release, color = TextMuted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("SRT")
                    if (result.downloadCount > 0) append(" · ${formatDownloadCount(result.downloadCount)} downloads")
                    if (result.rating > 0.0) append(" · ${String.format("%.1f", result.rating)}★")
                    result.fps?.let { append(" · ${it}fps") }
                },
                color = TextMuted, fontSize = 9.5.sp
            )

            val badges = buildList {
                if (isBestMatch) add("Best Match" to AmberCore)
                if (result.fromTrusted) add("Verified" to Color(0xFF6FCF97))
                result.sourceTag?.let { add(it to Color(0xFF56CCF2)) }
                if (result.hearingImpaired) add("SDH" to Color(0xFFBB86FC))
                if (result.forced) add("Forced" to Color(0xFFFF9800))
                if (result.machineTranslated || result.aiTranslated) add("Machine translated" to TextMuted)
            }
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(5.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    badges.forEach { (label, color) ->
                        Text(
                            text = label, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberCore)
                    .clickable { onDownloadAndApply() }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Apply", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpaceDeep)
                    .border(1.dp, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onDownloadOnly() }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TextBright, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Save only", color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

        // FIX (B4/B5): Hash Match and SubDL were previously mixed into
        // the small generic badge row at the bottom (same tiny size as
        // "Machine translated"), making them genuinely hard to spot in a
        // list of 50 results. Both are now a single prominent corner
        // badge, top-right, sized to actually stand out — Hash Match
        // takes priority when both would apply, since it's the stronger
        // confidence signal (exact file-byte match vs. "came from this
        // provider").
        val cornerBadge = when {
            result.hashMatch -> "Hash Match" to Color(0xFF6FCF97)
            result.provider == "SubDL" -> "SubDL" to Color(0xFFFFEE2A)
            else -> null
        }
        cornerBadge?.let { (label, color) ->
            Text(
                text = label, color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun IconCircleSmall2(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(22.dp).clip(CircleShape).background(GlassSurface).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = "Close", tint = TextBright, modifier = Modifier.size(12.dp))
    }
}

private fun formatDownloadCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

private fun friendlyLanguageDisplay2(code: String?): String = SubtitleLanguageRegistry.displayName(code)
