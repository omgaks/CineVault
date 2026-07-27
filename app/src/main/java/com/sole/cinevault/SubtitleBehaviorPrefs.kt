package com.sole.cinevault

import android.content.Context

// Minimal behaviour-preference storage — intentionally small right now.
// This is groundwork for the full "Subtitle behaviour settings" item
// (#10 on the roadmap: auto-download rules, file-matching patterns, "never
// auto-download for restricted folders", etc.) — these three fields are
// exposed in the Studio's Advanced tab and SAVED, but not yet READ by the
// download-search ranking or auto-download logic. Wiring them into actual
// behavior is #10's job, not this one's.
private const val BEHAVIOR_PREFS_NAME = "cinevault_subtitle_behavior"

data class SubtitleBehaviorPrefs(
    val preferredLanguage: String,
    val preferForced: Boolean,
    val preferSdh: Boolean
)

fun loadSubtitleBehaviorPrefs(context: Context): SubtitleBehaviorPrefs {
    val prefs = context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE)
    return SubtitleBehaviorPrefs(
        preferredLanguage = prefs.getString("preferredLanguage", "en") ?: "en",
        preferForced = prefs.getBoolean("preferForced", false),
        preferSdh = prefs.getBoolean("preferSdh", false)
    )
}

fun saveSubtitleBehaviorPrefs(context: Context, prefs: SubtitleBehaviorPrefs) {
    context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("preferredLanguage", prefs.preferredLanguage)
        .putBoolean("preferForced", prefs.preferForced)
        .putBoolean("preferSdh", prefs.preferSdh)
        .apply()
}package com.sole.cinevault

import android.content.Context

// Minimal behaviour-preference storage — intentionally small right now.
// This is groundwork for the full "Subtitle behaviour settings" item
// (#10 on the roadmap: auto-download rules, file-matching patterns, "never
// auto-download for restricted folders", etc.) — these three fields are
// exposed in the Studio's Advanced tab and SAVED, but not yet READ by the
// download-search ranking or auto-download logic. Wiring them into actual
// behavior is #10's job, not this one's.
private const val BEHAVIOR_PREFS_NAME = "cinevault_subtitle_behavior"

data class SubtitleBehaviorPrefs(
    val preferredLanguage: String,
    val preferForced: Boolean,
    val preferSdh: Boolean
)

fun loadSubtitleBehaviorPrefs(context: Context): SubtitleBehaviorPrefs {
    val prefs = context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE)
    return SubtitleBehaviorPrefs(
        preferredLanguage = prefs.getString("preferredLanguage", "en") ?: "en",
        preferForced = prefs.getBoolean("preferForced", false),
        preferSdh = prefs.getBoolean("preferSdh", false)
    )
}

fun saveSubtitleBehaviorPrefs(context: Context, prefs: SubtitleBehaviorPrefs) {
    context.getSharedPreferences(BEHAVIOR_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("preferredLanguage", prefs.preferredLanguage)
        .putBoolean("preferForced", prefs.preferForced)
        .putBoolean("preferSdh", prefs.preferSdh)
        .apply()
}
