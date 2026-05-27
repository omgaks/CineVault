package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ScanSource(
    val name: String,
    val pathKeyword: String,
    val defaultEnabled: Boolean = true
)

val defaultScanSources = listOf(
    ScanSource("Movies", "Movies/"),
    ScanSource("TV Shows", "TV Shows/"),
    ScanSource("Downloads", "Download/"),
    ScanSource("Anime", "Anime/"),
    ScanSource("Cinema", "Cinema/"),
    ScanSource("CineVault", "CineVault/"),
    ScanSource("Series", "Series/"),
    ScanSource("4K Movies", "4K Movies/"),
    ScanSource("DCIM / Camera", "DCIM/Camera/")
)

fun isVideoAllowedByScanSources(context: Context, videoPath: String): Boolean {
    val prefs = context.getSharedPreferences("scan_sources", Context.MODE_PRIVATE)

    return defaultScanSources.any { source ->
        val enabled = prefs.getBoolean(source.pathKeyword, source.defaultEnabled)
        enabled && videoPath.contains(source.pathKeyword, ignoreCase = true)
    }
}

fun saveCustomScanFolder(context: Context, folderUri: String) {
    val prefs = context.getSharedPreferences("scan_sources", Context.MODE_PRIVATE)
    val existing = prefs.getStringSet("custom_scan_folders", emptySet()) ?: emptySet()

    prefs.edit()
        .putStringSet("custom_scan_folders", (existing + folderUri).toSet())
        .apply()
}

fun removeCustomScanFolder(context: Context, folderUri: String) {
    val prefs = context.getSharedPreferences("scan_sources", Context.MODE_PRIVATE)
    val existing = prefs.getStringSet("custom_scan_folders", emptySet()) ?: emptySet()

    prefs.edit()
        .putStringSet("custom_scan_folders", existing.filterNot { it == folderUri }.toSet())
        .apply()
}

fun loadCustomScanFolders(context: Context): List<String> {
    val prefs = context.getSharedPreferences("scan_sources", Context.MODE_PRIVATE)
    return prefs.getStringSet("custom_scan_folders", emptySet())?.toList() ?: emptyList()
}

@Composable
fun ScanSourcesScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("scan_sources", Context.MODE_PRIVATE)
    }

    var refreshKey by remember { mutableStateOf(0) }
    var customFolders by remember {
        mutableStateOf(loadCustomScanFolders(context))
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            saveCustomScanFolder(context, uri.toString())
            customFolders = loadCustomScanFolders(context)
            refreshKey++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            text = "Scan Sources",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose which folders CineVault should scan.",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Default Sources",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        defaultScanSources.forEach { source ->
            val enabled = prefs.getBoolean(source.pathKeyword, source.defaultEnabled)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(
                        Color.White.copy(alpha = 0.07f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = source.pathKeyword,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        prefs.edit()
                            .putBoolean(source.pathKeyword, checked)
                            .apply()
                        refreshKey++
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Custom Folders",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { folderPicker.launch(null) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD36A),
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "+ Add Custom Folder",
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (customFolders.isEmpty()) {
            Text(
                text = "No custom folder added yet.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        } else {
            customFolders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Folder",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = folder,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "REMOVE",
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                removeCustomScanFolder(context, folder)
                                customFolders = loadCustomScanFolders(context)
                                refreshKey++
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Tip: After changing sources, rescan library.",
            color = Color(0xFFFFD36A),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(90.dp))
    }

    refreshKey
}