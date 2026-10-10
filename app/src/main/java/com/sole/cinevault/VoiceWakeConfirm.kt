package com.sole.cinevault

/**
 * Voice 2c-4: after the wake-word detector fires, the recording is turned into
 * words and the start of it is checked against the wake phrases. The check is
 * lenient on purpose: speech models often garble the invented word "CineVault"
 * ("Hey, Synavolta"), but ordinary talk should still be turned away.
 * Pure Kotlin, no Android types.
 */
private const val MAX_WAKE_WORDS = 3

private fun spokenTargets(enabledIds: Set<String>): List<String> {
    val targets = linkedSetOf("heycinevault", "heyvault")
    if ("cinevault" in enabledIds) targets.add("cinevault")
    if ("vault" in enabledIds) targets.add("vault")
    return targets.toList()
}

/**
 * A rough "how it sounds" form: c and k read as s, every vowel (and y) as one
 * vowel, repeats squashed. "CineVault" and "Synavolta" end up close together.
 */
private fun soundKey(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        val c = when (ch) {
            'c', 'k' -> 's'
            'a', 'e', 'i', 'o', 'u', 'y' -> 'a'
            else -> ch
        }
        if (out.isEmpty() || out.last() != c) out.append(c)
    }
    return out.toString()
}

private fun allowedEdits(keyLength: Int): Int = when {
    keyLength >= 9 -> 3
    keyLength >= 8 -> 2
    keyLength >= 6 -> 1
    else -> 0
}

private fun distance(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val temp = dp[j]
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = temp
        }
    }
    return dp[b.length]
}

/**
 * If [heard] starts with something that sounds like a wake phrase, returns the
 * rest (the command, possibly empty). Returns null when it does not.
 */
internal fun stripWakePhrase(heard: String, enabledIds: Set<String>): String? {
    val words = heard.lowercase().split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }.map { it.replace("'", "") }
    if (words.isEmpty()) return null
    val targets = spokenTargets(enabledIds)
    var bestWords = 0
    var bestDistance = Int.MAX_VALUE
    for (n in 1..minOf(MAX_WAKE_WORDS, words.size)) {
        val prefix = words.take(n).joinToString("")
        if (prefix.any { it.isDigit() }) break
        val prefixKey = soundKey(prefix)
        for (target in targets) {
            val targetKey = soundKey(target)
            val d = distance(prefixKey, targetKey)
            if (d <= allowedEdits(targetKey.length) && d < bestDistance) {
                bestDistance = d
                bestWords = n
            }
        }
    }
    if (bestWords == 0) return null
    return words.drop(bestWords).joinToString(" ")
}
