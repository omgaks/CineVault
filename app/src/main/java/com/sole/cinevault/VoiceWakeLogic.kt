package com.sole.cinevault

/**
 * Voice 2b: the wake phrases a person can tick on or off, the sound-alike
 * spellings behind each one, and the readout numbers. Pure Kotlin, no Android types.
 *
 * Why sound-alikes: the wake-word model only knows normal English spellings, so
 * "CineVault" is listed as "Sine Vault", "Cine Vault" and so on. These lines
 * are the model's own word pieces, prepared and checked ahead of time.
 */
internal enum class VoiceWakePhrase(
    val id: String,
    val label: String,
    val description: String,
    val defaultOn: Boolean,
    /** Model word-pieces and the keyword name the engine reports back. */
    val keywords: List<Pair<String, String>>
) {
    HeyCineVault(
        id = "hey_cinevault",
        label = "Hey CineVault",
        description = "The main wake phrase. Several sound-alike spellings are listened for.",
        defaultOn = true,
        keywords = listOf(
            "▁HE Y ▁S IN E ▁VA UL T" to "HEY_SINE_VAULT",
            "▁HE Y ▁C IN E ▁VA UL T" to "HEY_CINE_VAULT",
            "▁HE Y ▁S IN N Y ▁VA UL T" to "HEY_SINNY_VAULT"
        )
    ),
    HeyVault(
        id = "hey_vault",
        label = "Hey Vault",
        description = "A shorter backup the model recognises more easily.",
        defaultOn = true,
        keywords = listOf("▁HE Y ▁VA UL T" to "HEY_VAULT")
    ),
    CineVault(
        id = "cinevault",
        label = "CineVault",
        description = "Without \"Hey\". Mentioning the app in conversation can trigger it.",
        defaultOn = false,
        keywords = listOf(
            "▁S IN E ▁VA UL T" to "SINE_VAULT",
            "▁C IN E ▁VA UL T" to "CINE_VAULT"
        )
    ),
    Vault(
        id = "vault",
        label = "Vault",
        description = "Very short, so it can trigger by accident. Off unless the others fail.",
        defaultOn = false,
        keywords = listOf("▁VA UL T" to "VAULT")
    );
}

internal fun defaultWakePhraseIds(): Set<String> =
    VoiceWakePhrase.values().filter { it.defaultOn }.map { it.id }.toSet()

/** The keyword lines for the phrases ticked on. If none are ticked, the defaults are used. */
internal fun wakeKeywordLines(enabledIds: Set<String>): String {
    val ids = if (enabledIds.isEmpty()) defaultWakePhraseIds() else enabledIds
    return VoiceWakePhrase.values()
        .filter { it.id in ids }
        .flatMap { phrase -> phrase.keywords.map { (tokens, name) -> "$tokens @$name" } }
        .joinToString("\n")
}

/** Which phrase a reported keyword name belongs to. */
internal fun phraseForKeywordName(name: String): VoiceWakePhrase? =
    VoiceWakePhrase.values().firstOrNull { p -> p.keywords.any { it.second.equals(name.trim(), ignoreCase = true) } }

/**
 * Decides when to hand audio to the wake-word model. Opens as soon as speech is
 * likely and stays open for [hangoverMs] after the last speech, so the end of a
 * phrase is never cut off. Silence costs almost nothing.
 */
internal class VoiceGate(
    private val openProbability: Float = 0.5f,
    private val hangoverMs: Int = 1200
) {
    private var remainingMs = 0

    val isOpen: Boolean get() = remainingMs > 0

    /** Returns true while the gate is open after this window. */
    fun update(speechProbability: Float, windowMs: Int): Boolean {
        remainingMs = if (speechProbability >= openProbability) hangoverMs else (remainingMs - windowMs).coerceAtLeast(0)
        return isOpen
    }

    fun reset() { remainingMs = 0 }
}

/** What the diagnostics readout shows. Everything is counted since the last reset. */
internal class VoiceStats {
    var listenedMs: Long = 0; private set
    var gatedMs: Long = 0; private set
    var processingNs: Long = 0; private set
    private val hears = linkedMapOf<String, Int>()

    fun addListened(ms: Int) { listenedMs += ms }
    fun addGated(ms: Int) { gatedMs += ms }
    fun addProcessing(ns: Long) { processingNs += ns }
    fun addHear(phraseId: String) { hears[phraseId] = (hears[phraseId] ?: 0) + 1 }
    fun hearsFor(phraseId: String): Int = hears[phraseId] ?: 0
    val totalHears: Int get() = hears.values.sum()

    /** Share of the listening time the model was actually working (0..1). */
    fun processingShare(): Double =
        if (listenedMs <= 0) 0.0 else (processingNs / 1_000_000.0) / listenedMs

    /** Share of the listening time speech-like sound was present (0..1). */
    fun speechShare(): Double =
        if (listenedMs <= 0) 0.0 else gatedMs.toDouble() / listenedMs

    fun reset() {
        listenedMs = 0; gatedMs = 0; processingNs = 0; hears.clear()
    }
}

internal fun formatListened(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

internal fun formatPercent(share: Double): String {
    val pct = share * 100.0
    return when {
        pct <= 0.0 -> "0%"
        pct < 0.1 -> "<0.1%"
        pct < 10.0 -> "%.1f%%".format(java.util.Locale.US, pct)
        else -> "%d%%".format(java.util.Locale.US, Math.round(pct).toInt())
    }
}
