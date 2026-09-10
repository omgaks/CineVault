package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.library.loadPlaybackPosition
import com.sole.cinevault.library.recordWatchHistory
import com.sole.cinevault.library.updateRestrictedFolderLastPlayed
import com.sole.cinevault.segments.SmartSegmentResult
import com.sole.cinevault.subtitles.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Slice 47: owns the complete per-video startup/reset and subtitle restore
 * pipeline that previously lived as one large LaunchedEffect in
 * VideoPlayerScreen.
 */
data class PlayerVideoSessionSetters(
    val setMovieSubtitleMemoryReady: (Boolean) -> Unit,
    val setMovieAppearanceMemoryReady: (Boolean) -> Unit,
    val setRestoredDualNeedsApply: (Boolean) -> Unit,
    val setMovieSubtitleMemory: (MovieSubtitleMemory?) -> Unit,
    val setPosition: (Long) -> Unit,
    val setDuration: (Long) -> Unit,
    val setShowControls: (Boolean) -> Unit,
    val setShowTopBar: (Boolean) -> Unit,
    val setShowAudioSelector: (Boolean) -> Unit,
    val setShowSpeedMenu: (Boolean) -> Unit,
    val setShowSleepMenu: (Boolean) -> Unit,
    val setShowSrtBrowser: (Boolean) -> Unit,
    val setPendingNextEpisode: (VideoWithMetadata?) -> Unit,
    val setNextEpisodeCountdown: (Int) -> Unit,
    val setShowNextEpisodeOverlay: (Boolean) -> Unit,
    val setNextEpisodeDismissed: (Boolean) -> Unit,
    val setSmartSegmentResult: (SmartSegmentResult) -> Unit,
    val setPreviewBitmap: (Bitmap?) -> Unit,
    val setPreviewFrames: (List<VideoThumbnailHelper.PreviewFrame>) -> Unit,
    val setIsVideoEnded: (Boolean) -> Unit,
    val setPlayerErrorMessage: (String?) -> Unit,
    val setErrorRetryCount: (Int) -> Unit,
    val setStuckBufferingHint: (Boolean) -> Unit,
    val setAudioLanguageCheckedForPath: (String?) -> Unit,
    val setDualSecondaryColorHex: (String) -> Unit,
    val setAutoSyncStatus: (AutoSyncStatus) -> Unit,
    val setAutoSyncSpeechTimeline: (FloatArray?) -> Unit,
    val setDroppedFrameNudgeCount: (Int) -> Unit,
    val setLastNudgeAtMs: (Long) -> Unit,
)

@Composable
fun PlayerVideoSessionInitializationEffect(
    context: Context,
    scope: CoroutineScope,
    video: VideoFile,
    isStreamMedia: Boolean,
    isRestrictedFolderMedia: Boolean,
    canDownloadExternalSubtitles: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    driftUi: DriftCorrectionState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    autoSubtitleFetch: AutoSubtitleFetchState,
    setTextTracksDisabled: (Boolean) -> Unit,
    playVideoWithSubtitle: (Uri?, Long, Boolean) -> Unit,
    getCurrentSafeResumePosition: () -> Long,
    setters: PlayerVideoSessionSetters,
) {
    LaunchedEffect(video.path) {
        setters.setMovieSubtitleMemoryReady(false)
        setters.setMovieAppearanceMemoryReady(false)
        setters.setRestoredDualNeedsApply(false)

        val savedPosition =
            if (isStreamMedia) 0L
            else loadPlaybackPosition(context, video.path)

        val savedSubtitleMemory = withContext(Dispatchers.IO) {
            loadMovieSubtitleMemory(context, video.path)
        }
        setters.setMovieSubtitleMemory(savedSubtitleMemory)

        setters.setPosition(savedPosition)
        setters.setDuration(1L)
        setters.setShowControls(true)
        setters.setShowTopBar(true)
        setters.setShowAudioSelector(false)
        coreUi.showSettings = false
        trackUi.showSelector = false
        searchUi.showSearch = false
        setters.setShowSpeedMenu(false)
        setters.setShowSleepMenu(false)
        setters.setShowSrtBrowser(false)

        searchUi.showFallback = false
        searchUi.showEmbeddedBrowser = false
        searchUi.pendingImportCandidates = null
        searchUi.searchResults = emptyList()
        searchUi.searchStatus = ""
        searchUi.searchLoading = false

        setters.setPendingNextEpisode(null)
        setters.setNextEpisodeCountdown(0)
        setters.setShowNextEpisodeOverlay(false)
        setters.setNextEpisodeDismissed(false)
        setters.setSmartSegmentResult(SmartSegmentResult())
        setters.setPreviewBitmap(null)
        setters.setPreviewFrames(emptyList())
        setters.setIsVideoEnded(false)
        setters.setPlayerErrorMessage(null)
        setters.setErrorRetryCount(0)
        setters.setStuckBufferingHint(false)

        trackUi.originalUri = null
        trackUi.appliedOffsetMs = 0L
        coreUi.syncOffset = 0.0f
        driftUi.scale = 1.0f
        driftUi.appliedScale = 1.0f
        driftUi.pointA = null
        driftUi.pointB = null
        coreUi.dialogueSyncArmed = false
        coreUi.dialogueSyncReferenceMs = null
        driftUi.showDialog = false
        dualUi.enabled = false
        dualUi.statusText = ""
        trackUi.primaryUri = null
        trackUi.primaryLanguage = null
        setters.setAudioLanguageCheckedForPath(null)
        appearanceUi.preserveOriginalStyling = false
        studioUi.gestureFeedback = ""
        setters.setAutoSyncStatus(AutoSyncStatus.Idle)
        setters.setAutoSyncSpeechTimeline(null)
        trackUi.selectedKey = null
        trackUi.selectedLabel = ""
        trackUi.selectedSource = ""
        setters.setDroppedFrameNudgeCount(0)
        setters.setLastNudgeAtMs(0L)

        savedSubtitleMemory?.let { memory ->
            coreUi.subtitlesEnabled = memory.subtitlesEnabled
            coreUi.syncOffset = memory.syncOffsetSeconds
            dualUi.secondaryLanguage = memory.dualSecondaryLanguage
            dualUi.gapLines = memory.dualGapLines
            dualUi.secondarySourceLabel = memory.dualSecondarySource
            setters.setDualSecondaryColorHex(memory.dualSecondaryColorHex)
            appearanceUi.preserveOriginalStyling =
                memory.preserveOriginalStyling
        }

        if (!isStreamMedia) {
            recordWatchHistory(
                context,
                video.path,
                cleanVideoTitle(video.path),
            )
        }

        if (isRestrictedFolderMedia) {
            updateRestrictedFolderLastPlayed(
                context,
                video.path,
                video.folderPath,
            )
        }

        val rememberedPrimaryUri = savedSubtitleMemory
            ?.primaryUri
            ?.takeIf { canRestoreMovieSubtitleUri(context, it) }
            ?.let(Uri::parse)

        val restoredRememberedPrimary = rememberedPrimaryUri != null

        val localMatch =
            if (
                !restoredRememberedPrimary &&
                !isStreamMedia &&
                coreUi.behaviorPrefs.autoLoadMatchingLocalFile
            ) {
                withContext(Dispatchers.IO) {
                    findBestMatchingLocalSubtitle(
                        video.path,
                        coreUi.behaviorPrefs.preferredLanguages,
                    )
                }
            } else {
                null
            }

        val cachedSubtitle =
            if (
                !restoredRememberedPrimary &&
                localMatch == null &&
                !isStreamMedia &&
                canDownloadExternalSubtitles
            ) {
                withContext(Dispatchers.IO) {
                    OpenSubtitlesClient.findCachedSubtitle(
                        context,
                        video.path,
                        coreUi.behaviorPrefs.preferredLanguages,
                    )
                }
            } else {
                null
            }

        when {
            rememberedPrimaryUri != null &&
                savedSubtitleMemory != null -> {
                coreUi.subtitlesEnabled =
                    savedSubtitleMemory.subtitlesEnabled
                setTextTracksDisabled(!coreUi.subtitlesEnabled)

                trackUi.primaryUri = rememberedPrimaryUri
                trackUi.primaryLanguage =
                    savedSubtitleMemory.primaryLanguage
                trackUi.selectedKey = savedSubtitleMemory.selectedKey
                trackUi.selectedLabel = savedSubtitleMemory.selectedLabel
                trackUi.selectedSource = savedSubtitleMemory.selectedSource

                if (coreUi.subtitlesEnabled) {
                    playVideoWithSubtitle(
                        rememberedPrimaryUri,
                        savedPosition,
                        true,
                    )
                } else {
                    playVideoWithSubtitle(
                        null,
                        savedPosition,
                        true,
                    )
                }

                dualUi.enabled =
                    savedSubtitleMemory.dualEnabled &&
                        coreUi.subtitlesEnabled
                setters.setRestoredDualNeedsApply(dualUi.enabled)
                autoSubtitleFetch.attemptedForPath = video.path
            }

            localMatch != null -> {
                coreUi.subtitlesEnabled = true
                setTextTracksDisabled(false)

                val localUri = Uri.fromFile(localMatch.file)
                val cleanedLocalUri = withContext(Dispatchers.IO) {
                    buildCleanedSubtitleFile(
                        context,
                        localUri,
                        coreUi.cleaningOptions,
                    )
                } ?: localUri

                trackUi.primaryUri = cleanedLocalUri
                trackUi.primaryLanguage = localMatch.languageCode
                playVideoWithSubtitle(
                    cleanedLocalUri,
                    savedPosition,
                    true,
                )
                autoSubtitleFetch.attemptedForPath = video.path
                trackUi.selectedKey =
                    "local:${localMatch.file.absolutePath}"
                trackUi.selectedLabel = localMatch.file.name
                trackUi.selectedSource = "Local file"
            }

            cachedSubtitle != null -> {
                coreUi.subtitlesEnabled = true
                setTextTracksDisabled(false)

                val cleanedCachedUri = withContext(Dispatchers.IO) {
                    buildCleanedSubtitleFile(
                        context,
                        cachedSubtitle.uri,
                        coreUi.cleaningOptions,
                    )
                } ?: cachedSubtitle.uri

                trackUi.primaryUri = cleanedCachedUri
                trackUi.primaryLanguage = cachedSubtitle.language
                playVideoWithSubtitle(
                    cleanedCachedUri,
                    savedPosition,
                    true,
                )
                autoSubtitleFetch.attemptedForPath = video.path
                trackUi.selectedKey = "downloaded"
                trackUi.selectedLabel =
                    friendlyLanguageName(cachedSubtitle.language)
                trackUi.selectedSource = "OpenSubtitles"
            }

            else -> {
                playVideoWithSubtitle(
                    null,
                    savedPosition,
                    true,
                )
            }
        }

        if (
            shouldAutoDownloadSubtitleOnSessionStart(
                isStreamMedia = isStreamMedia,
                canDownloadExternalSubtitles = canDownloadExternalSubtitles,
                isRestrictedFolderMedia = isRestrictedFolderMedia,
                autoDownloadWhenMissing =
                    coreUi.behaviorPrefs.autoDownloadWhenMissing,
                restoredRememberedPrimary = restoredRememberedPrimary,
                hasCachedSubtitle = cachedSubtitle != null,
                hasLocalMatch = localMatch != null,
                alreadyAttemptedForPath =
                    autoSubtitleFetch.attemptedForPath == video.path,
            )
        ) {
            autoSubtitleFetch.attemptedForPath = video.path

            scope.launch {
                delay(playerSubtitleAutoFetchStartDelayMs())

                if (autoSubtitleFetch.downloadInProgress) {
                    return@launch
                }

                autoSubtitleFetch.downloadInProgress = true
                autoSubtitleFetch.status = "Searching subtitles..."

                try {
                    val result =
                        OpenSubtitlesClient.downloadBestSubtitleDetailed(
                            context,
                            video.path,
                            coreUi.behaviorPrefs.preferredLanguages,
                        )

                    if (result is SubtitleDownloadResult.Success) {
                        coreUi.subtitlesEnabled = true
                        setTextTracksDisabled(false)
                        autoSubtitleFetch.status = "Subtitle loaded"

                        val cleanedResultUri =
                            withContext(Dispatchers.IO) {
                                buildCleanedSubtitleFile(
                                    context,
                                    result.uri,
                                    coreUi.cleaningOptions,
                                )
                            } ?: result.uri

                        trackUi.primaryUri = cleanedResultUri
                        trackUi.primaryLanguage =
                            SubtitleLanguageRegistry.normalize(
                                result.language
                            )

                        playVideoWithSubtitle(
                            cleanedResultUri,
                            getCurrentSafeResumePosition(),
                            true,
                        )

                        trackUi.selectedKey = "downloaded"
                        trackUi.selectedLabel =
                            friendlyLanguageName(result.language)
                        trackUi.selectedSource = "OpenSubtitles"

                        delay(playerSubtitleStatusClearDelayMs())
                        autoSubtitleFetch.status = ""
                    } else {
                        autoSubtitleFetch.status = result.summary()
                        delay(playerSubtitleResultStatusDurationMs())
                        autoSubtitleFetch.status = ""
                    }
                } catch (e: Exception) {
                    autoSubtitleFetch.status =
                        "Subtitle failed: ${
                            e.message ?: e.javaClass.simpleName
                        }"
                    delay(playerSubtitleFailureStatusDurationMs())
                    autoSubtitleFetch.status = ""
                } finally {
                    autoSubtitleFetch.downloadInProgress = false
                }
            }
        }

        setters.setMovieSubtitleMemoryReady(true)
    }
}

/**
 * Pure gate for the expensive network auto-download path.
 */
fun shouldAutoDownloadSubtitleOnSessionStart(
    isStreamMedia: Boolean,
    canDownloadExternalSubtitles: Boolean,
    isRestrictedFolderMedia: Boolean,
    autoDownloadWhenMissing: Boolean,
    restoredRememberedPrimary: Boolean,
    hasCachedSubtitle: Boolean,
    hasLocalMatch: Boolean,
    alreadyAttemptedForPath: Boolean,
): Boolean =
    !isStreamMedia &&
        canDownloadExternalSubtitles &&
        !isRestrictedFolderMedia &&
        autoDownloadWhenMissing &&
        !restoredRememberedPrimary &&
        !hasCachedSubtitle &&
        !hasLocalMatch &&
        !alreadyAttemptedForPath
