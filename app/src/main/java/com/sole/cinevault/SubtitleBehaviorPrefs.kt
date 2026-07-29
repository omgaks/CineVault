package com.sole.cinevault

import android.content.Context

// Full "Subtitle behaviour settings" model — expanded from the earlier
// single-language/two-toggle version (#7's Advanced tab) now that this is
// actually being wired into real behavior rather than just stored.
//
// Two items from the original spec are deliberately NOT toggleable here:
// - "Never auto-download for personal/restricted-folder videos" — this was
//   already a hardcoded, unconditional rule in VideoPlayerScreen.kt before
//   this feature existed (see the `!isRestrictedFolderMedia` check gating
//   background auto-search). Left as a fixed safety default rather than a
//   togglable preference, since making it optional would mean a setting
//   could accidentally weaken a privacy protection.
// - "Prefer complete subtitles" — OpenSubtitles' search API doesn't return
//   a field indicating whether a subtitle is a full release or a partial
//   one, so there's no real data to rank on. Not faked with a no-op toggle.
data class SubtitleBehaviorPrefs(
    // Priority-ordered list of language codes — index 0 is tried first in
    // both auto-download and manual search defaults, falling through to
    // the next on a miss.
    val preferredLanguages: List<String> = listOf("en"),
    val preferForced: Boolean = false,
    val preferSdh: Boolean = false,
    val autoEnableEmbeddedSubtitles: Boolean = true,
    val autoLoadMatchingLocalFile: Boolean = true,
    val autoDownloadWhenMissing: Boolean = false,
    val rememberLastSelectedLanguage: Boolean = true,
    val disableWhenAudioMatchesPreferred: Boolean = false,
    // Off by default — see VideoPlayerScreen.kt's gesture-zone comment for
    // why this is opt-in rather than always-on: swipe/pinch/long-press
    // gestures on a region of the video screen carry real collision risk
    // with the existing brightness/volume/seek/zoom gestures already
    // living there, so the person has to deliberately choose this on.
    val enableSubtitleGestures: Boolean = false
)

private const val BEHAVIOR_PREFS_NAME = "cinevault_subtitle_behavior"

fun loadSubtitleBehaviorPrefs(context: Context): SubtitleBehaviorPrefs {
    val prefs = context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE)
    val languagesRaw = prefs.getString("preferredLanguages", null)
    val languages = languagesRaw?.split(",")?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf("en")
    return SubtitleBehaviorPrefs(
        preferredLanguages = languages,
        preferForced = prefs.getBoolean("preferForced", false),
        preferSdh = prefs.getBoolean("preferSdh", false),
        autoEnableEmbeddedSubtitles = prefs.getBoolean("autoEnableEmbedded", true),
        autoLoadMatchingLocalFile = prefs.getBoolean("autoLoadLocalMatch", true),
        autoDownloadWhenMissing = prefs.getBoolean("autoDownloadWhenMissing", false),
        rememberLastSelectedLanguage = prefs.getBoolean("rememberLastLanguage", true),
        disableWhenAudioMatchesPreferred = prefs.getBoolean("disableWhenAudioMatches", false),
        enableSubtitleGestures = prefs.getBoolean("enableSubtitleGestures", false)
    )
}

fun saveSubtitleBehaviorPrefs(context: Context, prefs: SubtitleBehaviorPrefs) {
    context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("preferredLanguages", prefs.preferredLanguages.joinToString(","))
        .putBoolean("preferForced", prefs.preferForced)
        .putBoolean("preferSdh", prefs.preferSdh)
        .putBoolean("autoEnableEmbedded", prefs.autoEnableEmbeddedSubtitles)
        .putBoolean("autoLoadLocalMatch", prefs.autoLoadMatchingLocalFile)
        .putBoolean("autoDownloadWhenMissing", prefs.autoDownloadWhenMissing)
        .putBoolean("rememberLastLanguage", prefs.rememberLastSelectedLanguage)
        .putBoolean("disableWhenAudioMatches", prefs.disableWhenAudioMatchesPreferred)
        .putBoolean("enableSubtitleGestures", prefs.enableSubtitleGestures)
        .apply()
}

// Moves `language` to the front of the priority list (if present, else
// prepends it), used when "remember last selected language" is on and the
// person picks a subtitle track — next video defaults to trying that
// language first.
fun promoteLanguageToFront(prefs: SubtitleBehaviorPrefs, language: String): SubtitleBehaviorPrefs {
    if (prefs.preferredLanguages.firstOrNull() == language) return prefs
    val reordered = listOf(language) + prefs.preferredLanguages.filterNot { it == language }
    return prefs.copy(preferredLanguages = reordered.take(6))
}
