package com.sole.cinevault.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffortlessSourceSheet(
    initialType: NetworkType = NetworkType.WEBDAV,
    initialAddress: String = "",
    onDismiss: () -> Unit,
    onSave: (SavedNetworkSource, NetworkCredential?) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var address by remember { mutableStateOf(initialAddress) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var rootPath by remember { mutableStateOf("/") }
    var port by remember { mutableStateOf("") }
    var hostKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121317),
        contentColor = TextBright,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("Add a network source", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Usually CineVault only needs the address. Sign in only when your server asks.", color = TextMuted)
            Spacer(Modifier.height(18.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceTypeChip(NetworkType.WEBDAV, "WebDAV", Icons.Rounded.Cloud, type) { type = it }
                SourceTypeChip(NetworkType.SFTP, "SFTP", Icons.Rounded.Security, type) { type = it }
                SourceTypeChip(NetworkType.HTTP_DIRECTORY, "Web", Icons.Rounded.Language, type) { type = it }
                SourceTypeChip(NetworkType.M3U, "M3U", Icons.Rounded.PlaylistPlay, type) { type = it }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = address, onValueChange = { address = it; error = null },
                label = { Text(addressLabel(type)) },
                placeholder = { Text(addressHint(type)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text("CineVault can name it for you") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            if (type == NetworkType.WEBDAV || type == NetworkType.SFTP) {
                Spacer(Modifier.height(16.dp))
                Text("Sign in • optional", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(secret, { secret = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            }

            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.Tune, null, tint = AmberCore)
                Spacer(Modifier.width(6.dp))
                Text(if (advanced) "Hide advanced" else "Advanced", color = AmberCore)
            }

            if (advanced && type == NetworkType.SFTP) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port • default 22") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(rootPath, { rootPath = it }, label = { Text("Folder • default /") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(hostKey, { hostKey = it }, label = { Text("Server SHA-256 fingerprint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("SFTP requires a trusted server fingerprint before CineVault connects.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    runCatching {
                        val normalized = normalizedManualAddress(type, address)
                        if (type == NetworkType.SFTP) require(hostKey.startsWith("SHA256:")) {
                            "Open Advanced and add the server SHA-256 fingerprint."
                        }
                        val sourceId = stableNetworkSourceId(type, normalized)
                        val source = SavedNetworkSource(
                            id = sourceId,
                            displayName = name.trim().ifBlank { suggestedSourceName(type, normalized) },
                            type = type,
                            address = normalized,
                            rootPath = if (type == NetworkType.SFTP) rootPath.ifBlank { "/" } else "",
                            port = if (type == NetworkType.SFTP) port.toIntOrNull() else null,
                            hostKeySha256 = if (type == NetworkType.SFTP) hostKey.trim() else "",
                        )
                        val credential = if (username.isNotBlank() || secret.isNotBlank()) NetworkCredential(username.trim(), secret) else null
                        onSave(source, credential)
                    }.onFailure { error = it.message ?: "Check the details and try again." }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberCore, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.AddCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Source", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SourceTypeChip(type: NetworkType, label: String, icon: ImageVector, selected: NetworkType, onClick: (NetworkType) -> Unit) {
    FilterChip(
        selected = selected == type, onClick = { onClick(type) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmberCore.copy(alpha = .18f),
            selectedLabelColor = TextBright,
            selectedLeadingIconColor = AmberCore,
        ),
    )
}

private fun addressLabel(type: NetworkType): String = when (type) {
    NetworkType.SFTP -> "Server"
    NetworkType.M3U -> "Playlist address"
    else -> "Server address"
}

private fun addressHint(type: NetworkType): String = when (type) {
    NetworkType.SFTP -> "nas.local"
    NetworkType.M3U -> "https://server/library.m3u"
    NetworkType.WEBDAV -> "https://server/webdav"
    else -> "https://server/videos/"
}
