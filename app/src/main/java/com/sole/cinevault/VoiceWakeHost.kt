package com.sole.cinevault

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay

internal fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Keeps the wake-word listener running only when all of these are true:
 * Voice is switched on, the microphone is allowed, CineVault is on screen,
 * and the phone's screen is on. Also shows the small "what I heard" pill.
 */
@Composable
internal fun VoiceWakeHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var visible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> visible = true
                Lifecycle.Event.ON_STOP -> visible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var screenOn by remember {
        mutableStateOf((context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> screenOn = true
                    Intent.ACTION_SCREEN_OFF -> screenOn = false
                }
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val enabled = VoiceRuntime.enabled
    var micAllowed by remember { mutableStateOf(hasMicrophonePermission(context)) }
    // Re-checked while Voice is on, so allowing the microphone later is noticed without a restart.
    LaunchedEffect(enabled, visible) {
        micAllowed = hasMicrophonePermission(context)
        while (enabled && visible) { delay(1500); micAllowed = hasMicrophonePermission(context) }
    }
    // The wake word needs the microphone. Ask once as soon as it is wanted, instead of
    // waiting for the first tap on the mic button (which is what used to trigger the prompt).
    var askedForMic by remember { mutableStateOf(false) }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> micAllowed = granted }
    LaunchedEffect(enabled, micAllowed, visible, VoiceRuntime.wakeListening) {
        if (enabled && visible && VoiceRuntime.wakeListening && !micAllowed && !askedForMic) {
            askedForMic = true
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val shouldListen = enabled && micAllowed && visible && screenOn &&
        VoiceRuntime.wakeListening && !VoiceRuntime.talkActive

    val listener = remember {
        VoiceWakeListener(
            context = context.applicationContext,
            phraseIds = { VoiceRuntime.phraseIds },
            onWake = { phrase, keyword ->
                VoiceRuntime.lastHeard = VoiceHeardEvent(
                    text = "Wake word heard: ${phrase?.label ?: keyword}",
                    atMs = System.currentTimeMillis()
                )
                VoiceRuntime.statsVersion++
                VoiceRuntime.wakeFallback = false
                VoiceRuntime.wakeRequest++
            },
            onSpeechCheck = {
                VoiceRuntime.wakeFallback = true
                VoiceRuntime.wakeRequest++
            },
            onStatus = { listening, error ->
                VoiceRuntime.isListening = listening
                if (error != null) VoiceRuntime.lastError = error else if (listening) VoiceRuntime.lastError = null
            }
        )
    }
    LaunchedEffect(shouldListen) {
        if (shouldListen) listener.start() else listener.stop()
    }
    DisposableEffect(Unit) { onDispose { listener.stop() } }

    // The readout refreshes about once a second while it is listening.
    LaunchedEffect(shouldListen) {
        while (shouldListen) {
            delay(1000)
            VoiceRuntime.statsVersion++
        }
    }

    // "What I heard" pill. Switchable in Settings.
    val heard = VoiceRuntime.lastHeard
    var pillVisible by remember { mutableStateOf(false) }
    LaunchedEffect(heard) {
        if (heard != null && VoiceRuntime.showHeard) {
            pillVisible = true
            delay(2600)
            pillVisible = false
        }
    }
    if (pillVisible && heard != null) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = heard.text,
                color = TextBright,
                fontSize = CineType.Label,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(Color(0xE610131C), CircleShape)
                    .border(1.dp, GelGold.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}
