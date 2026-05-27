package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AshSignatureFont = FontFamily(
    Font(R.font.great_vibes)
)

@Composable
fun SettingsScreen(
    onOpenScanSources: () -> Unit,
    onOpenStreamUrl: () -> Unit
) {
    val context = LocalContext.current
    var selectedFolders by remember {
        mutableStateOf(loadMediaFolders(context))
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val updated = selectedFolders + uri.toString()
            selectedFolders = updated.distinct()
            saveMediaFolders(context, selectedFolders)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "CineVault control room",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        SettingsHeroCard()

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionCard(
            title = "Scan Manager",
            subtitle = "Choose default scan sources and keep library clean."
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onOpenScanSources() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFD37A).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = Color(0xFFFFD37A),
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan Sources",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Movies, TV Shows, Downloads, Anime, Camera",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "OPEN",
                        color = Color(0xFFFFD37A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "After changing scan sources, go to Library and rescan.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionCard(
            title = "Stream Player",
            subtitle = "Play direct online video links."
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0D2A2A),
                                Color(0xFF111111)
                            )
                        )
                    )
                    .clickable { onOpenStreamUrl() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFD36A).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌐", fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stream URL",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Play MP4 / M3U8 / WEBM links instantly",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "OPEN",
                        color = Color(0xFFFFD37A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "For direct video links only. Torrent/magnet links are not supported.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionCard(
            title = "Media Library",
            subtitle = "Add folders where CineVault should scan videos."
        ) {
            Button(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.13f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.List,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text("Add Media Folder", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Selected Media Folders",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedFolders.isEmpty()) {
                Text(
                    text = "No folder selected yet.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                selectedFolders.forEach { folder ->
                    FolderRow(folder = folder)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionCard(
            title = "Support CineVault",
            subtitle = "A small thank you keeps the vault alive."
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF2A1A00),
                                Color(0xFF111111)
                            )
                        )
                    )
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.buymeacoffee.com/")
                        )
                        context.startActivity(intent)
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFC107)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☕", fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buy me a coffee",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Optional support / donate button",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Later we can replace this with your real Buy Me a Coffee / UPI / website link.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionCard(
            title = "About",
            subtitle = "Premium local cinema experience."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "CineVault v1.0",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Built for local videos, cinematic posters, subtitles, resume playback and a premium media library feel.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = "Ashish • May 2026",
            color = Color(0xFFFFD37A).copy(alpha = 0.92f),
            fontSize = 34.sp,
            fontFamily = AshSignatureFont,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun SettingsHeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(155.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF141414),
                        Color(0xFF1F1400),
                        Color(0xFF060606)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(
                text = "CineVault",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Premium Media Experience",
                color = Color(0xFFFFD37A),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Your personal cinema archive.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(86.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.cinevault_circle_logo),
                contentDescription = "CineVault Logo",
                modifier = Modifier.size(82.dp)
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        content()
    }
}

@Composable
private fun FolderRow(folder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.List,
            contentDescription = null,
            tint = Color(0xFFFFD37A),
            modifier = Modifier.size(17.dp)
        )

        Spacer(modifier = Modifier.width(9.dp))

        Text(
            text = folder,
            color = Color.LightGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun saveMediaFolders(context: Context, folders: List<String>) {
    context
        .getSharedPreferences("cinevault_settings", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("media_folders", folders.toSet())
        .apply()
}

private fun loadMediaFolders(context: Context): List<String> {
    return context
        .getSharedPreferences("cinevault_settings", Context.MODE_PRIVATE)
        .getStringSet("media_folders", emptySet())
        ?.toList()
        ?: emptyList()
}
