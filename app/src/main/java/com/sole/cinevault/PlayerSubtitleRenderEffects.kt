package com.sole.cinevault

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.TypedValue
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import com.sole.cinevault.subtitles.SubtitleFormat
import com.sole.cinevault.subtitles.buildCleanedSubtitleFile
import com.sole.cinevault.subtitles.detectSubtitleFormat
import com.sole.cinevault.subtitles.parseSubtitleFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * PlayerSubtitleRenderEffects.kt
 *
 * Owns the subtitle effects that turn a selected file and the current
 * appearance/timing state into what Media3 renders. Keeping these effects
 * together makes VideoPlayerScreen responsible for orchestration while this
 * file handles subtitle application details. Behaviour is intentionally
 * unchanged from the inline Stage 10 implementation.
 */

@Composable
internal fun PlayerSubtitleRenderEffects(
    context: Context,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    pendingSrtUri: Uri?,
    isAssOrSsaFormat: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    driftUi: DriftCorrectionState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    autoSubtitleFetch: AutoSubtitleFetchState,
    onPendingSrtUriChanged: (Uri?) -> Unit,
    onShowControls: () -> Unit,
    playCurrentVideoWithSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit,
) {
    LaunchedEffect(pendingSrtUri) {
        val uri = pendingSrtUri ?: return@LaunchedEffect
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        coreUi.subtitlesEnabled = true
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        val pickedFormat = detectSubtitleFormat(uri)
        val formatLabel = if (
            pickedFormat == SubtitleFormat.SRT || pickedFormat == SubtitleFormat.UNKNOWN
        ) {
            "Subtitle"
        } else {
            pickedFormat.label.substringBefore(" (")
        }
        autoSubtitleFetch.status = "$formatLabel loaded"

        val cleanedSrtUri = withContext(Dispatchers.IO) {
            buildCleanedSubtitleFile(context, uri, coreUi.cleaningOptions)
        } ?: uri
        trackUi.primaryUri = cleanedSrtUri

        val pickedFile = uri.path?.let { java.io.File(it) }
        trackUi.primaryLanguage = pickedFile?.name?.let { name ->
            parseSubtitleFilename(name).first
        }
        playCurrentVideoWithSubtitle(cleanedSrtUri, resumeAt, true)
        trackUi.selectedKey = "local:${pickedFile?.absolutePath ?: uri}"
        trackUi.selectedLabel = pickedFile?.name ?: "Subtitle file"
        trackUi.selectedSource = "Local file"
        coreUi.showSettings = false
        trackUi.showSelector = false
        onShowControls()

        Toast.makeText(context, "$formatLabel file loaded", Toast.LENGTH_SHORT).show()
        delay(1400)
        autoSubtitleFetch.status = ""
        onPendingSrtUriChanged(null)
    }

    LaunchedEffect(
        studioUi.playerView,
        appearanceUi.textSizeSp,
        appearanceUi.bottomPadding,
        appearanceUi.appearance,
        dualUi.enabled,
        appearanceUi.preserveOriginalStyling,
        isAssOrSsaFormat,
    ) {
        val subtitleView = studioUi.playerView?.subtitleView
        subtitleView?.setUserDefaultStyle()
        val useEmbeddedStyles = dualUi.enabled ||
            (appearanceUi.preserveOriginalStyling && isAssOrSsaFormat)
        subtitleView?.setApplyEmbeddedStyles(useEmbeddedStyles)
        subtitleView?.setApplyEmbeddedFontSizes(false)
        subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceUi.textSizeSp)
        subtitleView?.setBottomPaddingFraction(appearanceUi.bottomPadding)
        subtitleView?.setStyle(
            CaptionStyleCompat(
                appearanceUi.appearance.foregroundColor,
                appearanceUi.appearance.backgroundColor,
                AndroidColor.TRANSPARENT,
                appearanceUi.appearance.edgeType,
                appearanceUi.appearance.edgeColor,
                null,
            ),
        )
    }

    LaunchedEffect(coreUi.syncOffset, driftUi.scale, trackUi.originalUri) {
        val baseUri = trackUi.originalUri ?: return@LaunchedEffect
        if (!coreUi.subtitlesEnabled) return@LaunchedEffect

        val offsetMs = (coreUi.syncOffset * 1000f).toLong()
        if (
            offsetMs == trackUi.appliedOffsetMs &&
            driftUi.scale == driftUi.appliedScale
        ) {
            return@LaunchedEffect
        }

        delay(350)
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shiftedUri = withContext(Dispatchers.IO) {
            buildShiftedSubtitleFile(context, baseUri, offsetMs, driftUi.scale)
        }
        if (shiftedUri != null) {
            trackUi.appliedOffsetMs = offsetMs
            driftUi.appliedScale = driftUi.scale
            playCurrentVideoWithSubtitle(shiftedUri, resumeAt, false)
        }
    }
}
