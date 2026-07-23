package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/*
 * RematchDialog.kt
 *
 * "Fix Match" flow: search field -> candidate list -> tap to apply.
 * Owns its own state locally (no ViewModel) and calls the suspend
 * functions in RematchViewModel.kt directly via rememberCoroutineScope —
 * same pattern the rest of the app uses for TMDB/OMDB calls.
 *
 * Usage from DetailScreen:
 *
 *   var showRematch by remember { mutableStateOf(false) }
 *
 *   IconButton(onClick = { showRematch = true }) {
 *       Icon(Icons.Default.Search, contentDescription = "Fix Match")
 *   }
 *
 *   if (showRematch) {
 *       RematchDialog(
 *           currentItem = item, // your VideoWithMetadata for this screen
 *           onDismiss = { showRematch = false },
 *           onApplied = { updated ->
 *               showRematch = false
 *               // update whatever state holds `item` on DetailScreen with `updated`
 *           }
 *       )
 *   }
 *
 * NOTE ON STYLING: colors below are placeholders (PlaceholderAmberCore etc).
 * Swap for your real Color.kt / Glass.kt tokens and panel modifier to match
 * the rest of the app — I don't have those files, so I can't reference them
 * directly here.
 */

private val PlaceholderAmberCore = Color(0xFFF5A623)
private val PlaceholderSpaceBlack = Color(0xFF0A0A0F)
private val PlaceholderSpaceMid = Color(0xFF16161F)

@Composable
fun RematchDialog(
    currentItem: VideoWithMetadata,
    onDismiss: () -> Unit,
    onApplied: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(currentItem.title) }
    var candidates by remember { mutableStateOf<List<MatchCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            isLoading = true
            errorMessage = null
            hasSearched = true
            candidates = try {
                searchMovieCandidates(trimmed)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Search failed"
                emptyList()
            }
            isLoading = false
        }
    }

    fun applyCandidate(candidate: MatchCandidate) {
        scope.launch {
            isApplying = true
            errorMessage = null
            try {
                val updated = applyRematch(context, currentItem, candidate)
                onApplied(updated)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to apply match"
                isApplying = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PlaceholderSpaceMid)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Fix Match",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search TMDB title…", color = Color.Gray) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PlaceholderAmberCore)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlaceholderAmberCore,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading || isApplying -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PlaceholderAmberCore)
                        }
                    }
                    errorMessage != null -> {
                        Text("Error: $errorMessage", color = Color(0xFFE05A5A))
                    }
                    !hasSearched -> {
                        Text(
                            "Type a title and search to see candidates.",
                            color = Color.Gray
                        )
                    }
                    candidates.isEmpty() -> {
                        Text(
                            "No matches found. Try a shorter or different query.",
                            color = Color.Gray
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(candidates) { candidate ->
                                CandidateRow(
                                    candidate = candidate,
                                    onClick = { applyCandidate(candidate) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: MatchCandidate,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PlaceholderSpaceBlack)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = candidate.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" },
            contentDescription = candidate.title,
            modifier = Modifier
                .size(width = 46.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            candidate.releaseYear?.let {
                Text(text = it.toString(), color = Color.Gray, fontSize = 13.sp)
            }
            candidate.overview?.let {
                Text(
                    text = it,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}
