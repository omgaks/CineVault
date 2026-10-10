package com.sole.cinevault

/**
 * Plain, testable rules for the Home screen. No Android types here.
 */

/** "Good evening, Ash." / "Good evening." / after 10pm "Still up, Ash?" */
internal fun homeGreeting(hour: Int, name: String): String {
    val clean = name.trim()
    val lateNight = hour >= 22 || hour < 5
    return when {
        lateNight && clean.isNotEmpty() -> "Still up, $clean?"
        lateNight -> "Still up?"
        else -> {
            val base = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            if (clean.isNotEmpty()) "$base, $clean." else "$base."
        }
    }
}

internal fun homeDayPart(hour: Int): String = when {
    hour >= 22 || hour < 5 -> "NIGHT"
    hour < 12 -> "MORNING"
    hour < 17 -> "AFTERNOON"
    else -> "EVENING"
}

/** 150 -> "2H 30M", 120 -> "2H", 45 -> "45M". */
internal fun formatBudgetMinutes(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0 -> "${rest}M"
        rest == 0 -> "${h}H"
        else -> "${h}H ${rest}M"
    }
}

internal const val TONIGHT_MIN_MINUTES = 60
internal const val TONIGHT_MAX_MINUTES = 240
internal const val TONIGHT_DEFAULT_MINUTES = 120
internal val TONIGHT_OPTIONS_MINUTES = listOf(60, 90, 120, 180, 240)

/** 0f at the shortest budget, 1f at the longest. */
internal fun tonightGaugeFraction(budgetMinutes: Int): Float {
    val span = (TONIGHT_MAX_MINUTES - TONIGHT_MIN_MINUTES).toFloat()
    return ((budgetMinutes - TONIGHT_MIN_MINUTES) / span).coerceIn(0f, 1f)
}

/**
 * Items whose length fits inside the budget, best fit first (least spare time).
 * Items with an unknown length (<= 0 minutes) never count as fitting.
 */
internal fun <T> fitsWithin(items: List<T>, budgetMinutes: Int, minutesOf: (T) -> Int): List<Pair<T, Int>> =
    items.mapNotNull { item ->
        val length = minutesOf(item)
        if (length in 1..budgetMinutes) item to (budgetMinutes - length) else null
    }.sortedBy { it.second }

/** "4 min to spare" style text. */
internal fun spareText(spareMinutes: Int): String = when {
    spareMinutes <= 0 -> "Perfect fit"
    spareMinutes < 60 -> "$spareMinutes min to spare"
    else -> "${formatBudgetMinutes(spareMinutes).lowercase()} to spare"
}
