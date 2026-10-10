package com.sole.cinevault

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sole.cinevault.library.formatFileSize
import com.sole.cinevault.ui.theme.*
import java.io.File

private const val COPIES_PREFS = "cinevault_library_copies"
private const val COPIES_KEY = "main_paths"

internal fun loadMainCopyChoices(context: Context): Set<String> =
    context.getSharedPreferences(COPIES_PREFS, Context.MODE_PRIVATE)
        .getStringSet(COPIES_KEY, emptySet())?.toSet() ?: emptySet()

internal fun saveMainCopyChoices(context: Context, paths: Set<String>) {
    context.getSharedPreferences(COPIES_PREFS, Context.MODE_PRIVATE)
        .edit().putStringSet(COPIES_KEY, paths).apply()
}

/**
 * Lists every copy of one film. Nothing here deletes by itself: Delete only
 * hands the file to the normal "Delete file?" confirmation. Tapping outside
 * closes the sheet.
 */
@Composable
internal fun ManageCopiesSheet(
    copies: List<VideoWithMetadata>,
    mainPath: String,
    onMakeMain: (VideoWithMetadata) -> Unit,
    onDelete: (VideoWithMetadata) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .cineCard(radius = 26.dp, gel = GelGold)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${copies.size} copies",
                    color = TextBright,
                    fontFamily = NewsreaderFamily,
                    fontSize = CineType.Title,
                    modifier = Modifier.weight(1f)
                )
                CineCloseButton(onClick = onClose)
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "The main copy is the one shown in your library. Nothing is deleted unless you choose Delete and confirm.",
                    color = TextMuted,
                    fontSize = CineType.Label
                )
                copies.forEach { copy ->
                    val isMain = copy.video.path == mainPath
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isMain) CineBadge(text = "Main", gel = GelGold)
                            Text(
                                text = copy.video.name,
                                color = TextBright,
                                fontSize = CineType.Body,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = formatFileSize(File(copy.video.path).length()) + "  ·  " + copy.video.folderPath,
                            color = TextFaint,
                            fontSize = CineType.Caption,
                            maxLines = 2
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isMain) {
                                CineButton(text = "Make this the main one", onClick = { onMakeMain(copy) }, style = CineButtonStyle.Secondary)
                            }
                            CineButton(text = "Delete", onClick = { onDelete(copy) }, style = CineButtonStyle.Danger)
                        }
                    }
                }
            }
        }
    }
}
