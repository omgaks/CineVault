package com.sole.cinevault.collections

import android.content.Context

// Lives in the existing metadata-settings preference file, which Settings →
// "Reset Settings" already clears — so a reset naturally restores the default
// (new page ON) without touching the reset list.
private const val COLLECTION_PREFS_NAME = "cinevault_metadata_settings"
private const val COLLECTION_PAGE_V2_KEY = "collection_page_v2_enabled"

fun loadCollectionPageV2Enabled(context: Context): Boolean =
    context.getSharedPreferences(COLLECTION_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(COLLECTION_PAGE_V2_KEY, true)

fun saveCollectionPageV2Enabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(COLLECTION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(COLLECTION_PAGE_V2_KEY, enabled).apply()
}
