package com.sole.cinevault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.sole.cinevault.subtitles.SubtitleFormat
import com.sole.cinevault.subtitles.buildCleanedSubtitleFile
import com.sole.cinevault.subtitles.detectSubtitleFormat
import com.sole.cinevault.subtitles.parseSubtitleFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Slice 29: owns application of a local subtitle Uri that has already been
 * selected/imported by the UI.
 *
 * Cleaning, format labelling, language detection, track-state updates,
 * playback handoff and temporary status feedback all used to live directly
 * inside VideoPlayerScreen's pendingSrtUri LaunchedEffect.
 */
class PendingSubtitleApplyCoordinator(
    private val context: Context,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val autoSubtitleFetch: AutoSubtitleFetchState,
    private val getResumePosition: () -> Long,
    private val enableTextTracks: () -> Unit,
    private val playSubtitle: (Uri, Long) -> Unit,
    private val showControls: () -> Unit,
    private val clearPendingUri: () -> Unit,
) {
    suspend fun apply(uri: Uri) {
        val resumeAt = getResumePosition()
        coreUi.subtitlesEnabled = true
        enableTextTracks()

        val pickedFormat = detectSubtitleFormat(uri)
        val formatLabel =
            if (pickedFormat == SubtitleFormat.SRT || pickedFormat == SubtitleFormat.UNKNOWN) {
                "Subtitle"
            } else {
                pickedFormat.label.substringBefore(" (")
            }

        autoSubtitleFetch.status = "$formatLabel loaded"

        val cleanedUri = withContext(Dispatchers.IO) {
            buildCleanedSubtitleFile(context, uri, coreUi.cleaningOptions)
        } ?: uri

        trackUi.primaryUri = cleanedUri

        val pickedFile = uri.path?.let(::File)
        trackUi.primaryLanguage = pickedFile?.name?.let { name ->
            parseSubtitleFilename(name).first
        }

        playSubtitle(cleanedUri, resumeAt)

        trackUi.selectedKey = "local:${pickedFile?.absolutePath ?: uri}"
        trackUi.selectedLabel = pickedFile?.name ?: "Subtitle file"
        trackUi.selectedSource = "Local file"

        coreUi.showSettings = false
        trackUi.showSelector = false
        showControls()

        Toast.makeText(
            context,
            "$formatLabel file loaded",
            Toast.LENGTH_SHORT,
        ).show()

        delay(playerSubtitleStatusClearDelayMs())
        autoSubtitleFetch.status = ""
        clearPendingUri()
    }
}
