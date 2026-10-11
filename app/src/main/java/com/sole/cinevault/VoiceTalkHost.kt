package com.sole.cinevault

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

/** What the tap-to-talk card is showing. */
private sealed interface TalkUi {
    object Idle : TalkUi
    object Listening : TalkUi
    object Transcribing : TalkUi
    object NoModel : TalkUi
    data class Done(val heard: String?, val text: String, val tone: Color = GelMint) : TalkUi
    data class Message(val heard: String?, val text: String, val tone: Color = TextMuted) : TalkUi
    data class Pick(val heard: String?, val options: List<TitleCandidate>) : TalkUi
}

/**
 * Floating microphone button plus the small "Listening" card. Tap, say what
 * you want ("play Avengers Endgame"), and it plays. [films] are what can be
 * played by name; [onPlay] is given the key of the chosen film.
 */
@Composable
internal fun BoxScope.VoiceTalkHost(
    films: List<TitleCandidate>,
    buttonVisible: Boolean,
    onPlay: (String) -> Unit
) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<TalkUi>(TalkUi.Idle) }
    var session by remember { mutableStateOf<VoiceTalkSession?>(null) }
    var androidSession by remember { mutableStateOf<VoiceAndroidSession?>(null) }
    var token by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(0f) }
    val showHeard = VoiceRuntime.showHeard

    fun close() {
        token++
        session?.cancel()
        session = null
        androidSession?.cancel()
        androidSession = null
        ui = TalkUi.Idle
        VoiceRuntime.talkActive = false
    }

    /** Words (or null) are in. Works out what to do. Same for every engine. */
    fun handleText(rawText: String?, mine: Int, filmsNow: List<TitleCandidate>, wakeMode: Boolean = false) {
        if (mine != token) return
        var text = rawText
        if (wakeMode) {
            // The wake word was heard by the detector. Check the words say it too.
            val heardAll = text?.let { cleanTranscript(it) }
            val rest = heardAll?.let { stripWakePhrase(it, VoiceRuntime.phraseIds) }
            if (rest == null) {
                VoiceRuntime.stats.addReject()
                VoiceRuntime.statsVersion++
                if (VoiceRuntime.showHeard && heardAll != null) {
                    ui = TalkUi.Done(null, "Not the wake word. I heard \"$heardAll\".", TextMuted)
                } else {
                    close()
                }
                return
            }
            VoiceRuntime.stats.addConfirm()
            VoiceRuntime.statsVersion++
            if (rest.isBlank()) {
                ui = TalkUi.Message(null, "Yes? I heard the wake word but no command. Tap the microphone and say it, like \"play Dune\".")
                return
            }
            text = rest
        }
        val textNow = text
        val next: TalkUi? = if (textNow == null) {
                TalkUi.Message(null, "The speech model could not start. Try again, or re-download it in Settings.", GelRose)
            } else {
                val heard = cleanTranscript(textNow)
                when (val o = resolveTalk(textNow, filmsNow)) {
                    is TalkOutcome.PlayFilm -> {
                        val key = o.film.key
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onPlay(key) }
                        close(); TalkUi.Idle
                    }
                    is TalkOutcome.PickFilm -> TalkUi.Pick(heard, o.options)
                    is TalkOutcome.FilmNotFound -> TalkUi.Message(heard, "I couldn't find \"${o.query}\" in your library.")
                    is TalkOutcome.NeedsScreen -> TalkUi.Message(heard, "For safety, do that on screen. Voice never ${o.action}s anything.")
                    is TalkOutcome.PlayerCommand -> {
                        val command = o.command
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (mine == token) {
                                val controller = VoicePlayerRegistry.current
                                ui = if (controller == null) {
                                    TalkUi.Message(heard, "Understood: ${o.description}. Open a film first, then try again.")
                                } else {
                                    val done = controller.apply(command)
                                    if (done != null) TalkUi.Done(heard, done)
                                    else TalkUi.Message(heard, "I can't do \"${o.description}\" right now.")
                                }
                            }
                        }
                        null
                    }
                    is TalkOutcome.NotUnderstood -> TalkUi.Message(heard, "I didn't understand that. Try \"play\" and a film name.")
                    TalkOutcome.Silence -> TalkUi.Message(null, "I didn't hear anything. Tap the microphone and try again.")
                }
            }
            if (next != null) ui = next
    }

    fun startListening(wakeAudio: FloatArray? = null, forceWhisper: Boolean = false) {
        // After the wake word, Whisper double-checks the phrase if it is downloaded.
        val checkWake = wakeAudio != null && VoiceTranscriber.isReady(context)
        val choice = if (checkWake || (forceWhisper && VoiceTranscriber.isReady(context))) EngineChoice.UseWhisper
        else chooseEngine(VoiceRuntime.engine, androidOnDeviceSpeechReady(context), VoiceTranscriber.isReady(context))
        if (choice is EngineChoice.Unavailable) {
            ui = if (choice.reason == "NO_WHISPER_MODEL") TalkUi.NoModel else TalkUi.Message(null, choice.reason, GelRose)
            return
        }
        val mine = ++token
        VoiceRuntime.talkActive = true
        ui = TalkUi.Listening
        val filmsNow = films
        if (choice is EngineChoice.UseAndroid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val a = VoiceAndroidSession(
                context = context.applicationContext,
                onLevel = { level = it },
                onFinished = { text, code ->
                    if (mine == token) {
                        androidSession = null
                        VoiceRuntime.talkActive = false
                        when {
                            // Auto mode: this phone's speech pack is missing, so use Whisper instead.
                            code != null && code in setOf(1, 2, 11, 12, 13) &&
                                VoiceRuntime.engine == SpeechEngine.Auto &&
                                VoiceTranscriber.isReady(context) -> startListening(forceWhisper = true)
                            code == 6 || code == 7 -> ui = TalkUi.Message(null, describeAndroidSpeechError(code))
                            code != null -> ui = TalkUi.Message(null, describeAndroidSpeechError(code), GelRose)
                            else -> handleText(text ?: "", mine, filmsNow)
                        }
                    }
                }
            )
            androidSession = a
            a.start()
            return
        }
        val s = VoiceTalkSession(
            context = context.applicationContext,
            prefix = if (checkWake) wakeAudio else null,
            onLevel = { level = it },
            onFinished = { samples, error ->
                if (mine == token) {
                    session = null
                    VoiceRuntime.talkActive = false
                    when {
                        error != null -> ui = TalkUi.Message(null, "The microphone could not start: $error", GelRose)
                        samples == null -> ui = TalkUi.Message(null, "I didn't hear anything. Tap the microphone and try again.")
                        else -> {
                            ui = TalkUi.Transcribing
                            val text = runCatching { VoiceTranscriber.transcribe(context, samples) }.getOrNull()
                            handleText(text, mine, filmsNow, wakeMode = checkWake)
                        }
                    }
                }
            }
        )
        session = s
        s.start()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        else ui = TalkUi.Message(null, "Voice needs the microphone. Allow it in Android Settings.", GelRose)
    }

    fun onMicTap() {
        when (ui) {
            TalkUi.Listening -> { session?.stopNow(); androidSession?.stopNow() }
            TalkUi.Idle, is TalkUi.Message, is TalkUi.Pick, TalkUi.NoModel ->
                if (hasMicrophonePermission(context)) startListening()
                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            TalkUi.Transcribing, is TalkUi.Done -> Unit
        }
    }

    // Leaving the screen (or switching Voice off) always lets go of the microphone.
    DisposableEffect(Unit) { onDispose { token++; session?.cancel(); androidSession?.cancel(); VoiceRuntime.talkActive = false } }
    val allowed = VoiceRuntime.enabled && buttonVisible
    LaunchedEffect(allowed) { if (!allowed) close() }

    // A finished command shows its result for a moment, then the card goes away.
    val doneState = ui as? TalkUi.Done
    LaunchedEffect(doneState) {
        if (doneState != null) { kotlinx.coroutines.delay(1800); if (ui === doneState) close() }
    }

    // The film gets quieter while the microphone is listening.
    val talking = VoiceRuntime.talkActive
    LaunchedEffect(talking, VoicePlayerRegistry.current) { VoicePlayerRegistry.current?.duck(talking) }

    // Saying the wake word opens the Listening card, same as tapping the microphone.
    var handledWake by remember { mutableStateOf(VoiceRuntime.wakeRequest) }
    LaunchedEffect(VoiceRuntime.wakeRequest) {
        if (VoiceRuntime.wakeRequest != handledWake) {
            handledWake = VoiceRuntime.wakeRequest
            val heardBefore = VoiceRuntime.wakeAudio
            VoiceRuntime.wakeAudio = null
            if (allowed && ui == TalkUi.Idle && hasMicrophonePermission(context)) startListening(heardBefore)
        }
    }

    if (ui != TalkUi.Idle) {
        BackHandler { close() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
        )
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF010131C))
                    .border(1.dp, GelGold.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                    when (val state = ui) {
                        TalkUi.Listening -> {
                            Text("Listening…", color = GelGold, fontSize = CineType.Title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Say what you want, like \"play Avengers Endgame\". Tap the microphone when you're done.", color = TextMuted, fontSize = CineType.Caption, lineHeight = 17.sp)
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.15f + 0.85f * level.coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(GelGold)
                            )
                        }
                        is TalkUi.Done -> {
                            if (showHeard && state.heard != null) {
                                Text("I heard: \"${state.heard}\"", color = TextBright, fontSize = CineType.Body)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(state.text, color = state.tone, fontSize = CineType.Title, fontWeight = FontWeight.SemiBold)
                        }
                        TalkUi.Transcribing ->
                            Text("Working it out…", color = GelGold, fontSize = CineType.Title, fontWeight = FontWeight.SemiBold)
                        TalkUi.NoModel -> {
                            Text("Speech model needed", color = GelGold, fontSize = CineType.Title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Download a speech model first: it is the same free one used to generate subtitles (in the player, under speech subtitles). It works on this phone only.", color = TextMuted, fontSize = CineType.Body, lineHeight = 20.sp)
                        }
                        is TalkUi.Message -> {
                            if (showHeard && state.heard != null) {
                                Text("I heard: \"${state.heard}\"", color = TextBright, fontSize = CineType.Body)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(state.text, color = state.tone, fontSize = CineType.Body, lineHeight = 20.sp)
                        }
                        is TalkUi.Pick -> {
                            if (showHeard && state.heard != null) {
                                Text("I heard: \"${state.heard}\"", color = TextBright, fontSize = CineType.Body)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text("Which one?", color = GelGold, fontSize = CineType.Title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.options.forEach { option ->
                                    Text(
                                        text = option.title,
                                        color = TextBright,
                                        fontSize = CineType.Body,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0x22FFFFFF))
                                            .clickable { onPlay(option.key); close() }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                        TalkUi.Idle -> Unit
                    }
                }
                CineCloseButton(onClick = { close() }, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }

    val micShown = VoiceRuntime.showMic || !VoiceRuntime.wakeListening
    val inPlayer = VoicePlayerRegistry.current != null
    val playerChrome = !inPlayer || VoicePlayerRegistry.current?.controlsVisible?.invoke() == true
    if (VoiceRuntime.enabled && micShown && ((buttonVisible && playerChrome) || ui != TalkUi.Idle)) {
        val listening = ui == TalkUi.Listening
        Box(
            modifier = Modifier
                .align(if (inPlayer) Alignment.CenterEnd else Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(end = 16.dp, bottom = if (inPlayer) 0.dp else 96.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(if (listening) GelCoral else GelGold)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onMicTap() }
                .semantics { contentDescription = if (listening) "Stop listening" else "Talk to CineVault" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = SpaceBlack, modifier = Modifier.size(28.dp))
        }
    }
}
