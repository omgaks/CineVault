package com.sole.cinevault.subtitles

import android.content.Context

private const val CLEANING_PREFS_NAME = "cinevault_subtitle_cleaning"

fun loadSubtitleCleaningOptions(context: Context): SubtitleCleaningOptions {
    val prefs = context.getSharedPreferences(CLEANING_PREFS_NAME, Context.MODE_PRIVATE)
    return SubtitleCleaningOptions(
        hideHearingImpairedDescriptions = prefs.getBoolean("hideHearingImpaired", false),
        removeSpeakerNames = prefs.getBoolean("removeSpeakerNames", false),
        fixBrokenLineBreaks = prefs.getBoolean("fixBrokenLineBreaks", false),
        mergeVeryShortLines = prefs.getBoolean("mergeVeryShortLines", false),
        correctEncodingSymbols = prefs.getBoolean("correctEncodingSymbols", false),
        removeHtmlTags = prefs.getBoolean("removeHtmlTags", false),
        convertAllCaps = prefs.getBoolean("convertAllCaps", false),
        removeDuplicateLines = prefs.getBoolean("removeDuplicateLines", false)
    )
}

fun saveSubtitleCleaningOptions(context: Context, options: SubtitleCleaningOptions) {
    context.getSharedPreferences(CLEANING_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean("hideHearingImpaired", options.hideHearingImpairedDescriptions)
        .putBoolean("removeSpeakerNames", options.removeSpeakerNames)
        .putBoolean("fixBrokenLineBreaks", options.fixBrokenLineBreaks)
        .putBoolean("mergeVeryShortLines", options.mergeVeryShortLines)
        .putBoolean("correctEncodingSymbols", options.correctEncodingSymbols)
        .putBoolean("removeHtmlTags", options.removeHtmlTags)
        .putBoolean("convertAllCaps", options.convertAllCaps)
        .putBoolean("removeDuplicateLines", options.removeDuplicateLines)
        .apply()
}
