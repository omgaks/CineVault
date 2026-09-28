package com.sole.cinevault.network

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NetworkHubIntegratedScreen(
    onBack: () -> Unit,
    onShareLibrary: () -> Unit,
    onOpenSource: (NetworkSource) -> Unit,
    onCineVaultPeer: (DiscoveredNetworkDevice, CineVaultQrPayload?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val integration = remember(context) { NetworkHubIntegration(context) }
    val qrScanner = remember(context) { CineVaultQrScanner(context) }
    val nearby = remember(context) { CineVaultNearbyRuntime.get(context) }
    val directClient = remember { CineVaultDirectClient() }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    val summaries = remember(refreshKey) { integration.summaries() }
    var showSetup by remember { mutableStateOf(false) }
    var setupType by remember { mutableStateOf(NetworkType.WEBDAV) }
    var setupAddress by remember { mutableStateOf("") }
    var showMediaServerSetup by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf(false) }

    fun pairCineVault(device: DiscoveredNetworkDevice, qr: CineVaultQrPayload?) {
        val endpoint = device.addressHint ?: run {
            message = "This CineVault device did not provide a usable Nearby address."
            return
        }
        if (pairing) return
        pairing = true
        message = "Requesting access from ${device.displayName}…"
        scope.launch {
            try {
                val request = createNearbyPairRequest(nearby.deviceId, nearby.deviceName, qr)
                    ?: error("Could not create pairing request.")
                val initial = directClient.requestPairing(endpoint, request)
                if (initial.state == "error") error(initial.message ?: "Pairing request failed.")

                var approved: CineVaultPairResponseEnvelope? = initial.takeIf { it.state == "approved" }
                var attempts = 0
                while (approved == null && attempts < 120) {
                    delay(750)
                    attempts++
                    val result = directClient.pollApproval(endpoint, request.remoteDeviceId, request.inviteNonce)
                    when (result.state) {
                        "approved" -> approved = result
                        "error", "denied" -> error(result.message ?: "The sharing device declined the request.")
                    }
                }
                val result = approved ?: error("Approval timed out. Try again when the other device is ready.")
                val token = result.sessionToken ?: error("The approved session did not contain an access token.")
                val expiry = result.expiresAtEpochMs ?: error("The approved session did not contain an expiry.")
                val session = NearbyPairingSession(
                    remoteDeviceId = device.id,
                    sessionToken = token,
                    expiresAtEpochMs = expiry,
                )
                message = null
                onOpenSource(
                    CineVaultDirectNetworkSource(
                        displayName = device.displayName,
                        endpoint = endpoint,
                        session = session,
                    )
                )
            } catch (t: Throwable) {
                message = t.message ?: "Could not connect to this CineVault device."
            } finally {
                pairing = false
            }
        }
    }

    fun handleDevice(device: DiscoveredNetworkDevice, qr: CineVaultQrPayload? = null) {
        when (val action = discoveredDeviceAction(device)) {
            is DiscoveredDeviceAction.OpenDlna -> onOpenSource(action.source)
            is DiscoveredDeviceAction.PrefillManual -> when (action.type) {
                NetworkType.JELLYFIN, NetworkType.EMBY -> {
                    message = "This server was discovered. Sign in once to continue."
                    showMediaServerSetup = true
                }
                else -> {
                    setupType = action.type
                    setupAddress = action.address
                    showSetup = true
                }
            }
            is DiscoveredDeviceAction.CineVaultPeer -> {
                onCineVaultPeer(action.device, qr)
                pairCineVault(action.device, qr)
            }
            is DiscoveredDeviceAction.Unsupported -> message = action.reason
        }
    }

    NetworkHubScreen(
        savedSources = summaries,
        onBack = onBack,
        onFindDevices = {},
        onScanQr = {
            qrScanner.scan(
                onResult = { raw ->
                    val payload = parseCineVaultQrPayload(raw)
                    when {
                        payload == null -> message = "That QR code is not a valid CineVault Nearby invite."
                        payload.expiresAtEpochMs <= System.currentTimeMillis() ->
                            message = "That CineVault invite has expired. Ask the other device to create a new one."
                        else -> handleDevice(payload.asDiscoveredDevice(), payload)
                    }
                },
                onError = { message = it },
            )
        },
        onAddFileShare = { setupType=NetworkType.WEBDAV; setupAddress=""; showSetup=true },
        onAddMediaServer = { showMediaServerSetup=true },
        onAddWebSource = { setupType=NetworkType.HTTP_DIRECTORY; setupAddress=""; showSetup=true },
        onShareLibrary = onShareLibrary,
        onSourceClick = { id -> integration.resolve(id)?.let(onOpenSource) },
        onDiscoveredDeviceClick = { handleDevice(it) },
    )

    if (showSetup) EffortlessSourceSheet(
        initialType=setupType, initialAddress=setupAddress,
        onDismiss={showSetup=false},
        onSave={source,credential ->
            integration.save(source,credential); refreshKey++; showSetup=false
            integration.resolve(source.id)?.let(onOpenSource)
        }
    )

    if (showMediaServerSetup) MediaServerSetupSheet(
        onDismiss={showMediaServerSetup=false},
        onConnected={source,session ->
            integration.saveMediaServer(source,session); refreshKey++; showMediaServerSetup=false
            integration.resolve(source.id)?.let(onOpenSource)
        }
    )

    message?.let { body ->
        AlertDialog(
            onDismissRequest={ if(!pairing) message=null },
            confirmButton={ if(!pairing) TextButton(onClick={message=null}){Text("OK",color=AmberCore)} },
            title={Text(if(pairing)"Waiting for approval" else "Network",color=TextBright)},
            text={Text(body,color=TextMuted)},
            containerColor=Color(0xFF15161A),
        )
    }
}
