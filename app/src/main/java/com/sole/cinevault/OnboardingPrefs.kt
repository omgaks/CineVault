package com.sole.cinevault

import android.content.Context

/** Remembers that the first-run flow was finished or skipped. Not part of the Reset list. */
private const val ONBOARDING_PREFS = "cinevault_onboarding"
private const val KEY_DONE = "done"

internal fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

internal fun markOnboardingDone(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DONE, true).apply()
}
