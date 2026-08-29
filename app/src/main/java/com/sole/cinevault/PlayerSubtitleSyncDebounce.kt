package com.sole.cinevault

/**
 * Preserves the existing debounce delay before rebuilding a shifted subtitle
 * file after sync or drift changes.
 */
internal fun playerSubtitleSyncDebounceMs(): Long = 350L
