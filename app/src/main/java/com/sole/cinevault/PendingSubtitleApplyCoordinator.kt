package com.sole.cinevault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import com.sole.cinevault.subtitles.SubtitleFormat
import com.sole.cinevault.subtitles.buildCleanedSubtitleFile
import com.sole.cinevault.subtitles.detectSubtitleFormat
import com.sole.cinevault.subtitles.parseSubtitleFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Owns application of a local/generated subtitle Uri selected by the UI. */
class PendingSubtitleApplyCoordinator(
    private val context: Context,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val autoSubtitleFetch: AutoSubtitleFetchState,
    private val getResumePosition: () -> Long,
    private val enableTextTracks: (String?) -> Unit,
    private val playSubtitle: (Uri, Long) -> Unit,
    private val showControls: () -> Unit,
    private val clearPendingUri: () -> Unit,
) {
    suspend fun apply(uri: Uri) {
        val resumeAt = getResumePosition()
        coreUi.subtitlesEnabled = true
        val pickedFile = uri.path?.let(::File)
        val pickedLanguage = pickedFile?.name?.let { name -> parseSubtitleFilename(name).first }
        enableTextTracks(pickedLanguage)

        val pickedFormat = detectSubtitleFormat(uri)
        if (pickedFormat == SubtitleFormat.UNKNOWN) {
            autoSubtitleFetch.status = "Unsupported subtitle format"
            Toast.makeText(context, "Unsupported subtitle format", Toast.LENGTH_LONG).show()
            clearPendingUri()
            return
        }
        val formatLabel = if (pickedFormat == SubtitleFormat.SRT) "Subtitle" else pickedFormat.label.substringBefore(" (")
        autoSubtitleFetch.status = "$formatLabel loaded"

        val cleanedUri = withContext(Dispatchers.IO) {
            buildCleanedSubtitleFile(context, uri, coreUi.cleaningOptions)
        } ?: uri

        trackUi.primaryUri = cleanedUri
        trackUi.primaryLanguage = pickedLanguage

        // Set identity before the handoff so the MediaItem is built with the
        // selected language and UI never temporarily falls back to Embedded.
        val generated = uri.path?.contains("generated-subtitles") == true ||
            pickedFile?.name?.contains("translated", ignoreCase = true) == true
        trackUi.selectedKey = if (generated) "generated:${pickedFile?.name ?: uri}" else "local:${pickedFile?.absolutePath ?: uri}"
        trackUi.selectedLabel = pickedFile?.name ?: "Subtitle file"
        trackUi.selectedSource = if (generated) "Generated" else "Local file"

        playSubtitle(cleanedUri, resumeAt)

        coreUi.showSettings = false
        trackUi.showSelector = false
        showControls()
        Toast.makeText(context, "$formatLabel file loaded", Toast.LENGTH_SHORT).show()

        delay(playerSubtitleStatusClearDelayMs())
        autoSubtitleFetch.status = ""
        clearPendingUri()
    }
}
