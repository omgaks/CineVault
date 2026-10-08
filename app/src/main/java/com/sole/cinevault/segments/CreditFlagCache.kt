package com.sole.cinevault.segments

import android.content.Context

/**
 * Remembers "this film has a mid / post credits scene" after the first time it
 * is learned, so the "stay for the extra scene" notice keeps working with no
 * network. Stored locally only; nothing is uploaded.
 */
internal object CreditFlagCodec {
    fun encode(mid: Boolean, post: Boolean): String = "${if (mid) 1 else 0},${if (post) 1 else 0}"

    fun decode(raw: String?): Pair<Boolean, Boolean>? {
        val parts = raw?.split(",") ?: return null
        if (parts.size != 2) return null
        val mid = parts[0].trim()
        val post = parts[1].trim()
        if (mid !in setOf("0", "1") || post !in setOf("0", "1")) return null
        return (mid == "1") to (post == "1")
    }
}

internal class CreditFlagCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("cinevault_credit_flags", Context.MODE_PRIVATE)

    fun get(mediaKey: String): Pair<Boolean, Boolean>? =
        runCatching { CreditFlagCodec.decode(prefs.getString(mediaKey, null)) }.getOrNull()

    fun put(mediaKey: String, mid: Boolean, post: Boolean) {
        runCatching { prefs.edit().putString(mediaKey, CreditFlagCodec.encode(mid, post)).apply() }
    }
}
