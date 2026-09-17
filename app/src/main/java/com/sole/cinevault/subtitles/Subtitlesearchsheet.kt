package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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

/**
 * 1B.2R — Smart Search interaction repair.
 *
 * The old root-level detectTapGestures{} competed with TextField, clickable
 * search actions and the result scrollers. The popup now moves only from a
 * long-press on its header; all normal touches belong to the content.
 */
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
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {}
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var season by remember(initialSeason) { mutableStateOf(initialSeason) }
    var episode by remember(initialEpisode) { mutableStateOf(initialEpisode) }
    var showManualFields by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val maxOffsetXPx = with(density) {
        ((containerWidth - popupWidth) / 2).coerceAtLeast(0.dp).toPx()
    }
    val maxOffsetYPx = with(density) {
        ((containerHeight - popupMaxHeight) / 2).coerceAtLeast(0.dp).toPx()
    }

    fun fireSearch() {
        onUserInteraction()
        onSearch(query.trim(), season, episode)
    }

    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    dragOffsetX.roundToInt(),
                    dragOffsetY.roundToInt()
                )
            }
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .glassPanel(
                cornerRadius = 20.dp,
                fill = SpaceMid.copy(alpha = 0.84f)
            )
            .border(
                1.dp,
                AmberCore.copy(alpha = 0.20f),
                RoundedCornerShape(20.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )
                            onUserInteraction()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onUserInteraction()
                            dragOffsetX =
                                (dragOffsetX + dragAmount.x)
                                    .coerceIn(
                                        -maxOffsetXPx,
                                        maxOffsetXPx
                                    )
                            dragOffsetY =
                                (dragOffsetY + dragAmount.y)
                                    .coerceIn(
                                        -maxOffsetYPx,
                                        maxOffsetYPx
                                    )
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    text = "‹",
                    color = AmberCore,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AmberCore.copy(alpha = 0.10f))
                        .clickable {
                            onUserInteraction()
                            onBack()
                        }
                        .padding(
                            horizontal = 8.dp,
                            vertical = 1.dp
                        )
                )
                Spacer(Modifier.width(7.dp))
            }

            Text(
                text = "SMART SEARCH",
                color = AmberCore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        AmberCore.copy(alpha = 0.28f),
                        RoundedCornerShape(50)
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 5.dp
                    )
            )

            Spacer(Modifier.width(7.dp))
            SearchCloseButton {
                onUserInteraction()
                onDismiss()
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onUserInteraction()
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = TextBright
            ),
            placeholder = {
                Text(
                    "Movie or show title",
                    fontSize = 12.5.sp,
                    color = TextMuted
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = AmberCore,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { fireSearch() }
                        .padding(1.dp)
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

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (showManualFields)
                        AmberGlow.copy(alpha = 0.16f)
                    else
                        SpaceDeep.copy(alpha = 0.6f)
                )
                .border(
                    1.dp,
                    AmberCore.copy(
                        alpha = if (showManualFields) 0.6f else 0.25f
                    ),
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    onUserInteraction()
                    showManualFields = !showManualFields
                }
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
        ) {
            Text(
                text =
                    if (showManualFields)
                        "▾ TV show: Season / Episode"
                    else
                        "▸ TV show? Set Season / Episode",
                color = AmberCore,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showManualFields) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = season,
                    onValueChange = {
                        season = it.filter(Char::isDigit)
                        onUserInteraction()
                    },
                    singleLine = true,
                    label = { Text("Season", fontSize = 9.sp) },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = episode,
                    onValueChange = {
                        episode = it.filter(Char::isDigit)
                        onUserInteraction()
                    },
                    singleLine = true,
                    label = { Text("Episode", fontSize = 9.sp) },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Search",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore)
                    .clickable { fireSearch() }
                    .padding(
                        horizontal = 14.dp,
                        vertical = 6.dp
                    )
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(Modifier.height(6.dp))

        when {
            isSearching -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = AmberCore,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Searching…",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            results.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = statusText.ifBlank {
                            "No results yet — try Search"
                        },
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    Text(
                        text = "Search website",
                        color = AmberCore,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(
                                1.dp,
                                AmberCore.copy(alpha = 0.4f),
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                onUserInteraction()
                                onWebsiteFallback()
                            }
                            .padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            )
                    )
                }
            }

            else -> {
                val subDlResults =
                    results.filter { it.provider == "SubDL" }
                val openSubsResults =
                    results.filter { it.provider != "SubDL" }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchProviderColumn(
                        title = "SubDL (${subDlResults.size})",
                        titleColor = Color(0xFFFFEE2A),
                        emptyText = "No SubDL results",
                        results = subDlResults,
                        onInteraction = onUserInteraction,
                        onDownloadAndApply = onDownloadAndApply,
                        onDownloadOnly = onDownloadOnly
                    )

                    SearchProviderColumn(
                        title = "OpenSubtitles (${openSubsResults.size})",
                        titleColor = Color(0xFF56CCF2),
                        emptyText = "No OpenSubtitles results",
                        results = openSubsResults,
                        onInteraction = onUserInteraction,
                        onDownloadAndApply = onDownloadAndApply,
                        onDownloadOnly = onDownloadOnly
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SearchProviderColumn(
    title: String,
    titleColor: Color,
    emptyText: String,
    results: List<SubtitleSearchResult>,
    onInteraction: () -> Unit,
    onDownloadAndApply: (SubtitleSearchResult) -> Unit,
    onDownloadOnly: (SubtitleSearchResult) -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (results.isEmpty()) {
            Text(
                text = emptyText,
                color = TextMuted,
                fontSize = 11.sp
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                results.forEachIndexed { index, result ->
                    SubtitleResultCard(
                        result = result,
                        isBestMatch = index == 0,
                        onDownloadAndApply = {
                            onInteraction()
                            onDownloadAndApply(result)
                        },
                        onDownloadOnly = {
                            onInteraction()
                            onDownloadOnly(result)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
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
                    if (isBestMatch)
                        Brush.verticalGradient(
                            listOf(
                                AmberGlow.copy(alpha = 0.75f),
                                AmberDeep.copy(alpha = 0.30f)
                            )
                        )
                    else
                        Brush.verticalGradient(
                            listOf(
                                GlassBorderTop,
                                GlassBorderBottom
                            )
                        ),
                    RoundedCornerShape(14.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = SubtitleLanguageRegistry.displayName(result.language),
                color = TextBright,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 56.dp)
            )

            Text(
                text = result.release,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    append("SRT")
                    if (result.downloadCount > 0) {
                        append(" · ${formatDownloadCount(result.downloadCount)} downloads")
                    }
                    if (result.rating > 0.0) {
                        append(" · ${String.format("%.1f", result.rating)}★")
                    }
                    result.fps?.let { append(" · ${it}fps") }
                },
                color = TextMuted,
                fontSize = 9.5.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AmberCore)
                        .clickable { onDownloadAndApply() }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Apply",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpaceDeep)
                        .border(
                            1.dp,
                            AmberCore.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onDownloadOnly() }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = TextBright,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Save only",
                        color = TextBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val cornerBadge = when {
            result.hashMatch ->
                "Hash Match" to Color(0xFF6FCF97)
            result.provider == "SubDL" ->
                "SubDL" to Color(0xFFFFEE2A)
            else -> null
        }

        cornerBadge?.let { (label, color) ->
            Text(
                text = label,
                color = Color.Black,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}

@Composable
private fun SearchCloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore)
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.Black,
            modifier = Modifier.size(15.dp)
        )
    }
}

private fun formatDownloadCount(count: Int): String = when {
    count >= 1_000_000 ->
        String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 ->
        String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}
