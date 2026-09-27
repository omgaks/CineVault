package com.sole.cinevault.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaServerSetupSheet(
    onDismiss: () -> Unit,
    onConnected: (SavedNetworkSource, MediaServerSession) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(MediaServerKind.JELLYFIN) }
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!connecting) onDismiss() },
        containerColor = Color(0xFF121317),
        contentColor = TextBright,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row {
                Icon(Icons.Rounded.Dns, null, tint = AmberCore)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Connect a media server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Jellyfin or Emby • sign in once, then CineVault remembers it securely.", color = TextMuted)
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == MediaServerKind.JELLYFIN,
                    onClick = { kind = MediaServerKind.JELLYFIN; error = null },
                    label = { Text("Jellyfin") },
                )
                FilterChip(
                    selected = kind == MediaServerKind.EMBY,
                    onClick = { kind = MediaServerKind.EMBY; error = null },
                    label = { Text("Emby") },
                )
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; error = null },
                label = { Text("Server address") },
                placeholder = { Text("192.168.1.20:8096") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text("CineVault can name it for you") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (connecting) return@Button
                    scope.launch {
                        connecting = true
                        error = null
                        try {
                            val type = if (kind == MediaServerKind.JELLYFIN) NetworkType.JELLYFIN else NetworkType.EMBY
                            val normalized = normalizedManualAddress(type, address)
                            val api = MediaServerApi(normalized, kind)
                            val session = api.authenticate(username.trim(), password).getOrThrow()
                            val source = SavedNetworkSource(
                                id = stableNetworkSourceId(type, api.baseUrl),
                                displayName = name.trim().ifBlank { suggestedSourceName(type, api.baseUrl) },
                                type = type,
                                address = api.baseUrl,
                            )
                            onConnected(source, session)
                        } catch (t: Throwable) {
                            error = t.message?.take(160) ?: "Could not connect. Check the server and sign-in details."
                        } finally {
                            connecting = false
                        }
                    }
                },
                enabled = !connecting && address.isNotBlank() && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberCore, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
