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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShareLibraryScreen(
    onBack: () -> Unit,
    hostDeviceId: String,
    hostDeviceName: String,
    pendingRequest: NearbyPairingRequest? = null,
    onSessionApproved: (NearbyPairingSession) -> Unit = {},
) {
    val context = LocalContext.current
    val runtime = remember(context) { CineVaultNearbyRuntime.get(context) }
    val scope = rememberCoroutineScope()
    val connectState by runtime.connectState.collectAsState()
    val state = connectState.sharing
    var folders by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedOnly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { folders = runtime.availableFolders() }
    LaunchedEffect(state.running) {
        while (state.running) {
            runtime.refresh(state)
            delay(650)
        }
    }

    fun backOnly() = onBack()
    BackHandler(onBack = ::backOnly)

    Box(Modifier.fillMaxSize().background(SpaceBlack)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = AmberCore,
                    modifier = Modifier.background(AmberCore.copy(alpha=.10f), RoundedCornerShape(14.dp))
                        .clickable(onClick=::backOnly).padding(10.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Share My Library", color=TextBright, fontSize=25.sp, fontWeight=FontWeight.Bold)
                    Text("Private nearby sharing • approval required", color=TextMuted, fontSize=12.sp)
                }
            }

            Spacer(Modifier.height(22.dp))
            ShareCard("What can be shared", Icons.Rounded.FolderShared) {
                ShareChoice("Entire library", "Vault / Restricted content is always excluded.", !selectedOnly) {
                    selectedOnly=false; selectedFolders=emptySet()
                }
                Spacer(Modifier.height(8.dp))
                ShareChoice("Selected folders", "Choose exactly which normal folders are visible.", selectedOnly) {
                    selectedOnly=true
                }
                if (selectedOnly) {
                    Spacer(Modifier.height(10.dp))
                    folders.forEach { (id,name) ->
                        val chosen=id in selectedFolders
                        ShareChoice(name, if(chosen) "Included" else "Not shared", chosen) {
                            selectedFolders = if(chosen) selectedFolders-id else selectedFolders+id
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            ShareCard("Nearby sharing", Icons.Rounded.Devices) {
                if (!state.running) {
                    ShareButton("START SHARING") {
                        scope.launch {
                            runCatching {
                                val selection = if(selectedOnly)
                                    runtime.selectionForFolders(selectedFolders)
                                else ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY)
                                runtime.startSharing(selection)
                            }.onFailure { error=it.message ?: "Could not start Nearby sharing." }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("No server or discovery advertisement runs until you start sharing.", color=TextFaint, fontSize=12.sp)
                } else {
                    Text("VISIBLE NEARBY", color=Color(0xFF55D98B), fontSize=12.sp, fontWeight=FontWeight.Black)
                    Text(runtime.deviceName, color=TextBright, fontSize=15.sp, fontWeight=FontWeight.SemiBold)
                    Text("Another CineVault can simply use Find Devices — no address typing.", color=TextMuted, fontSize=12.sp)
                    state.qrOrNull(runtime)?.let {
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) { CineVaultInviteQr(it) }
                        Spacer(Modifier.height(7.dp))
                        Text("Or scan this code from CineVault → Network → Scan QR.", color=TextFaint, fontSize=11.sp)
                    }
                }

                state.pending?.let { request ->
                    Spacer(Modifier.height(16.dp))
                    Text("${request.remoteDeviceName} wants access", color=TextBright, fontSize=16.sp, fontWeight=FontWeight.Bold)
                    Text("Nothing is shared until you approve.", color=TextMuted, fontSize=12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        ShareButton("APPROVE") {
                            val approvedState = runtime.approve(state)
                            approvedState.approved?.let(onSessionApproved)
                        }
                        ShareButton("DENY") { runtime.deny(state) }
                    }
                }
            }

            state.approved?.let { approved ->
                Spacer(Modifier.height(16.dp))
                ShareCard("Connected securely", Icons.Rounded.Security) {
                    Text("Temporary Nearby session active", color=Color(0xFF55D98B), fontWeight=FontWeight.Bold)
                    Text("Access expires automatically. Vault / Restricted content remains excluded.", color=TextMuted, fontSize=12.sp)
                }
            }

            if(state.running) {
                Spacer(Modifier.height(16.dp))
                ShareButton("STOP SHARING") { runtime.stopSharing() }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    error?.let { msg ->
        AlertDialog(onDismissRequest={error=null}, confirmButton={ TextButton(onClick={error=null}){Text("OK")} },
            title={Text("Nearby sharing")}, text={Text(msg)})
    }
}

private fun CineVaultSharingState.qrOrNull(runtime: CineVaultNearbyRuntime): String? = runtime.qrPayload(this)

@Composable
private fun ShareCard(title:String, icon:androidx.compose.ui.graphics.vector.ImageVector, content:@Composable()->Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF121317), RoundedCornerShape(22.dp))
        .border(1.dp, AmberCore.copy(alpha=.18f), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=AmberCore); Spacer(Modifier.width(9.dp))
            Text(title,color=TextBright,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(13.dp)); content()
    }
}
@Composable
private fun ShareChoice(title:String, subtitle:String, selected:Boolean, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth()
        .background(if(selected) AmberCore.copy(alpha=.10f) else Color.White.copy(alpha=.035f), RoundedCornerShape(16.dp))
        .border(1.dp,if(selected) AmberCore.copy(alpha=.45f) else Color.Transparent,RoundedCornerShape(16.dp))
        .clickable(onClick=onClick).padding(13.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)){ Text(title,color=TextBright,fontSize=14.sp,fontWeight=FontWeight.SemiBold); Text(subtitle,color=TextMuted,fontSize=12.sp) }
        Text(if(selected)"SELECTED" else "SELECT",color=if(selected)AmberCore else TextFaint,fontSize=10.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable
private fun ShareButton(text:String,onClick:()->Unit) {
    Text(text,color=Color.Black,fontSize=12.sp,fontWeight=FontWeight.Black,
        modifier=Modifier.background(AmberCore,RoundedCornerShape(50)).clickable(onClick=onClick).padding(horizontal=15.dp,vertical=10.dp))
}
