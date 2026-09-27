package com.sole.cinevault.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

/**
 * D16-S6 closure surface for CineVault-to-CineVault sharing.
 *
 * This UI deliberately does not fake LAN transport. S5 supplied the privacy
 * policy; S6 exposes that policy as an explicit user-controlled flow. Real
 * discovery/transport can feed requests into this surface later without
 * weakening approval, expiry or vault-exclusion rules.
 */
@Composable
fun ShareLibraryScreen(
    onBack: () -> Unit,
    hostDeviceId: String,
    hostDeviceName: String,
    pendingRequest: NearbyPairingRequest? = null,
    onSessionApproved: (NearbyPairingSession) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val policy = remember { NearbyPairingPolicy() }
    var invite by remember { mutableStateOf<NearbyPairingInvite?>(null) }
    var session by remember { mutableStateOf<NearbyPairingSession?>(null) }
    var shareScope by remember { mutableStateOf(SharedLibraryScope.ENTIRE_LIBRARY) }

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ArrowBack, "Back", tint = AmberCore,
                    modifier = Modifier
                        .background(AmberCore.copy(alpha = .10f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onBack).padding(10.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Share My Library", color = TextBright, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Private, temporary access. Nothing connects automatically.", color = TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(22.dp))
            ShareCard("What can be shared", Icons.Rounded.FolderShared) {
                ShareChoice(
                    "Entire library",
                    "Secret/Vault content is always excluded.",
                    shareScope == SharedLibraryScope.ENTIRE_LIBRARY
                ) { shareScope = SharedLibraryScope.ENTIRE_LIBRARY }
                Spacer(Modifier.height(8.dp))
                ShareChoice(
                    "Selected folders",
                    "Only folders you explicitly approve.",
                    shareScope == SharedLibraryScope.SELECTED_FOLDERS
                ) { shareScope = SharedLibraryScope.SELECTED_FOLDERS }
            }

            Spacer(Modifier.height(16.dp))
            ShareCard("Pair a device", Icons.Rounded.Devices) {
                val currentInvite = invite
                if (currentInvite == null) {
                    ShareButton("START PAIRING") {
                        invite = policy.createInvite(hostDeviceId, hostDeviceName)
                        session = null
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Pairing invitations expire automatically.", color = TextFaint, fontSize = 12.sp)
                } else {
                    Text("CineVault is ready for an approved device.", color = TextBright, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Invite expires in about 2 minutes.", color = TextMuted, fontSize = 12.sp)

                    val request = pendingRequest
                    if (request != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(request.remoteDeviceName, color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("This device is asking to pair. Discovery alone does not grant access.", color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShareButton("APPROVE") {
                                val approved = policy.approve(currentInvite, request, userApproved = true)
                                if (approved != null) {
                                    session = approved
                                    onSessionApproved(approved)
                                }
                            }
                            ShareButton("DENY") { invite = null; session = null }
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Waiting for a device request. No library data is exposed while waiting.",
                            color = TextFaint, fontSize = 12.sp
                        )
                    }
                }
            }

            val active = session
            if (active != null) {
                Spacer(Modifier.height(16.dp))
                ShareCard("Paired securely", Icons.Rounded.Security) {
                    Text("Temporary session approved.", color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Device: ${active.remoteDeviceId}", color = TextMuted, fontSize = 12.sp)
                    Text("Session expires automatically; Vault content remains excluded.", color = TextFaint, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    ShareButton("STOP SHARING") { session = null; invite = null }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ShareCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF121317), RoundedCornerShape(22.dp))
            .border(1.dp, AmberCore.copy(alpha = .18f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AmberCore)
            Spacer(Modifier.width(9.dp))
            Text(title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(13.dp))
        content()
    }
}

@Composable
private fun ShareChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) AmberCore.copy(alpha = .10f) else Color.White.copy(alpha = .035f), RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) AmberCore.copy(alpha = .45f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text(if (selected) "SELECTED" else "SELECT", color = if (selected) AmberCore else TextFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShareButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.background(AmberCore, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 10.dp)
    )
}
