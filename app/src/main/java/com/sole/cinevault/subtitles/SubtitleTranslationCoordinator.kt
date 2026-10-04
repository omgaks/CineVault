package com.sole.cinevault.subtitles

import com.sole.cinevault.CineVaultToast

import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SubtitleTranslationStatus {
    object Idle : SubtitleTranslationStatus()
    data class Translating(val phase: String, val percent: Int) : SubtitleTranslationStatus()
    data class Ready(val uri: Uri, val language: String, val cueCount: Int) : SubtitleTranslationStatus()
    data class Failed(val reason: String) : SubtitleTranslationStatus()
}


/**
 * Slice 20: shared glue for generated subtitle sources.
 *
 * VideoPlayerScreen previously owned two separate responsibilities here:
 * resolving the currently selected subtitle into a translation-readable source,
 * and mutating all track-selection fields when Speech-to-Subs / AI Translation /
 * the generated library produced a new subtitle. Keeping those mutations here
 * makes every generated-subtitle path use the same application behavior.
 */
class GeneratedSubtitleOrchestrator(
    private val getResumePosition: () -> Long,
    private val getPrimaryUri: () -> Uri?,
    private val getOriginalUri: () -> Uri?,
    private val getSelectedKey: () -> String?,
    private val getSelectedLabel: () -> String,
    private val getSelectedSource: () -> String,
    private val getPrimaryLanguage: () -> String?,
    private val setSubtitlesEnabled: (Boolean) -> Unit,
    private val setPrimaryUri: (Uri) -> Unit,
    private val setOriginalUri: (Uri) -> Unit,
    private val setPrimaryLanguage: (String?) -> Unit,
    private val setSelectedKey: (String) -> Unit,
    private val setSelectedLabel: (String) -> Unit,
    private val setSelectedSource: (String) -> Unit,
    private val prepareExplicitTextSelection: (String?) -> Unit,
    private val playWithSubtitle: (Uri, Long) -> Unit,
) {
    fun resolveActiveSubtitle(): SubtitleSourceResolver.Resolved? =
        SubtitleSourceResolver.resolve(
            SubtitleSourceResolver.Snapshot(
                primaryUri = getPrimaryUri(),
                originalUri = getOriginalUri(),
                selectedKey = getSelectedKey(),
                selectedLabel = getSelectedLabel(),
                selectedSource = getSelectedSource(),
                primaryLanguage = getPrimaryLanguage(),
            )
        )

    fun apply(
        file: GeneratedSubtitleFile,
        language: String?,
        sourceLabel: String,
    ) {
        val resumeAt = getResumePosition()

        setSubtitlesEnabled(true)
        setPrimaryUri(file.uri)
        setOriginalUri(file.uri)
        setPrimaryLanguage(language)
        // Generated files are first-class CineVault tracks. Keep the same identity
        // used by Tracks/Manage so ACTIVE state and the rendered Media3 track cannot
        // diverge after AI Translation or Speech-to-Subs completes.
        setSelectedKey("generated:${file.fileName}")
        setSelectedLabel(file.label)
        setSelectedSource(sourceLabel)

        // An embedded preferred track may still be selected in DefaultTrackSelector.
        // Explicitly clear that preference before replacing the MediaItem, otherwise
        // Media3 can keep rendering embedded English while the UI says Generated.
        prepareExplicitTextSelection(language)
        playWithSubtitle(file.uri, resumeAt)
    }
}

class SubtitleTranslationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getCurrentVideoPath: () -> String,
    private val resolveActiveSubtitle: () -> SubtitleSourceResolver.Resolved?,
    private val getStatus: () -> SubtitleTranslationStatus,
    private val setStatus: (SubtitleTranslationStatus) -> Unit,
    private val onSubtitleReady: (GeneratedSubtitleFile, String) -> Unit,
    private val onGeneratedLibraryChanged: () -> Unit,
    // The embedded subtitle currently selected, if any: it is read out of the movie first.
    private val getEmbeddedRef: () -> EmbeddedSubtitleRef? = { null },
) {
    private var translationJob: Job? = null
    private var translationGeneration = 0L

    fun translateActive(target: SubtitleTranslationEngine.SupportedLanguage) {
        if (translationJob?.isActive == true) return

        val generation = ++translationGeneration

        val source = resolveActiveSubtitle()
        val embeddedRef = if (source == null) getEmbeddedRef() else null
        if (source == null && embeddedRef == null) {
            val reason =
                "No readable external subtitle is active. Load a Subtitle Studio download, local SRT, or generated subtitle first."
            setStatus(SubtitleTranslationStatus.Failed(reason))
            CineVaultToast.show(context, reason, long = true)
            return
        }

        setStatus(SubtitleTranslationStatus.Translating("Starting", 0))
        val videoPath = getCurrentVideoPath()

        translationJob = scope.launch {
            try {
                var activeSource: SubtitleSourceResolver.Resolved? = source
                if (activeSource == null && embeddedRef != null) {
                    // Embedded track: save its text as an SRT first, then translate that.
                    setStatus(SubtitleTranslationStatus.Translating("Reading embedded subtitle", 0))
                    val extracted = withContext(Dispatchers.IO) {
                        EmbeddedSubtitleExtractor.extract(context, videoPath, embeddedRef) { pct ->
                            scope.launch(Dispatchers.Main.immediate) {
                                if (generation == translationGeneration &&
                                    getStatus() is SubtitleTranslationStatus.Translating
                                ) {
                                    setStatus(
                                        SubtitleTranslationStatus.Translating("Reading embedded subtitle", pct)
                                    )
                                }
                            }
                        }
                    }
                    if (generation != translationGeneration) return@launch
                    when (extracted) {
                        is EmbeddedSubtitleExtractor.Result.Failure -> {
                            setStatus(SubtitleTranslationStatus.Failed(extracted.reason))
                            CineVaultToast.show(context, extracted.reason, long = true)
                            return@launch
                        }
                        is EmbeddedSubtitleExtractor.Result.Success -> {
                            activeSource = SubtitleSourceResolver.Resolved(
                                uri = Uri.fromFile(extracted.file),
                                language = extracted.language ?: embeddedRef.language,
                                label = "Embedded subtitle",
                                source = "Embedded",
                            )
                        }
                    }
                }
                val resolvedSource = activeSource ?: return@launch
                val knownSource =
                    resolvedSource.language?.let(SubtitleTranslationEngine::mlKitCodeForWhisperLanguage)

                val srtText = withContext(Dispatchers.IO) {
                    readTextFromUri(context, resolvedSource.uri)
                }

                if (srtText.isNullOrBlank()) {
                    setStatus(
                        SubtitleTranslationStatus.Failed(
                            "The active subtitle could not be read as text."
                        )
                    )
                    return@launch
                }

                var lastPhase = ""
                var lastPercent = -1

                val result = withContext(Dispatchers.Default) {
                    SubtitleTranslationEngine.translate(
                        srtText = srtText,
                        targetMlKitCode = target.mlKitCode,
                        sourceMlKitCode = knownSource,
                        onProgress = { progress ->
                            // Translation can emit a progress callback for every SRT cue.
                            // Do not mutate Compose state directly from Dispatchers.Default,
                            // and do not trigger hundreds of recompositions per second.
                            val shouldPublish =
                                progress.phase != lastPhase ||
                                    progress.percent >= lastPercent + 2 ||
                                    progress.percent == 100

                            if (shouldPublish) {
                                lastPhase = progress.phase
                                lastPercent = progress.percent

                                scope.launch(Dispatchers.Main.immediate) {
                                    // This callback is deliberately launched on the
                                    // UI scope, so it can outlive the worker that
                                    // produced it. Ignore it once that worker has
                                    // failed, completed, or been cancelled.
                                    if (
                                        generation == translationGeneration &&
                                        getStatus() is SubtitleTranslationStatus.Translating
                                    ) {
                                        setStatus(
                                            SubtitleTranslationStatus.Translating(
                                                progress.phase,
                                                progress.percent,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                if (generation != translationGeneration) return@launch

                when (result) {
                    is SubtitleTranslationEngine.Result.Failed -> {
                        setStatus(SubtitleTranslationStatus.Failed(result.reason))
                        CineVaultToast.show(context, "Translation failed: ${result.reason}", long = true)
                    }

                    is SubtitleTranslationEngine.Result.Success -> {
                        val generated = withContext(Dispatchers.IO) {
                            GeneratedSubtitleStore.write(
                                context = context,
                                videoPath = videoPath,
                                srtText = result.srtText,
                                labelSuffix = "translated-${target.mlKitCode}",
                            )
                        }

                        if (generated == null) {
                            setStatus(
                                SubtitleTranslationStatus.Failed(
                                    "Translation finished but the subtitle file couldn't be saved."
                                )
                            )
                            return@launch
                        }

                        if (getCurrentVideoPath() != videoPath) {
                            setStatus(SubtitleTranslationStatus.Idle)
                            return@launch
                        }

                        setStatus(
                            SubtitleTranslationStatus.Ready(
                                uri = generated.uri,
                                language = target.mlKitCode,
                                cueCount = generated.cueCount,
                            )
                        )
                        onGeneratedLibraryChanged()
                        onSubtitleReady(generated, target.mlKitCode)

                    }
                }
            } catch (_: CancellationException) {
                // cancelTranslation() invalidates the generation and publishes
                // Idle immediately. Only handle cancellation here when it came
                // from some other owner (for example, the parent scope).
                if (generation == translationGeneration) {
                    setStatus(SubtitleTranslationStatus.Idle)
                    CineVaultToast.show(context, "Translation stopped")
                }
            } catch (oom: OutOfMemoryError) {
                setStatus(
                    SubtitleTranslationStatus.Failed(
                        "Translation ran out of available memory. Try again after closing other apps."
                    )
                )
                CineVaultToast.show(context, "Translation stopped: not enough memory", long = true)
            } catch (t: Throwable) {
                val detail = t.message
                    ?.takeIf { it.isNotBlank() }
                    ?: t.javaClass.simpleName

                setStatus(
                    SubtitleTranslationStatus.Failed(
                        "Translation failed safely: $detail"
                    )
                )
                CineVaultToast.show(context, "Translation failed: $detail", long = true)
            } finally {
                if (generation == translationGeneration) {
                    translationJob = null
                }
            }
        }
    }

    /**
     * Forget the saved copy of the embedded subtitle so the next translation reads it out of the
     * movie again (with visible "Reading subtitle · N%" progress).
     */
    fun rereadEmbeddedNextTime() {
        val ref = getEmbeddedRef()
        if (ref == null) {
            CineVaultToast.show(context, "Pick an embedded subtitle track first")
            return
        }
        val removed = EmbeddedSubtitleExtractor.clearCache(context, getCurrentVideoPath(), ref)
        CineVaultToast.show(
            context,
            if (removed) "Will re-read the subtitle from the movie next time"
            else "Nothing saved yet — the next translation reads it from the movie",
        )
    }

    fun cancelTranslation() {
        val activeJob = translationJob?.takeIf { it.isActive } ?: return

        // Invalidate queued progress callbacks before cancelling the worker.
        // Publishing Idle here makes the Stop button respond immediately even
        // when an ML Kit Task takes time to observe coroutine cancellation.
        translationGeneration++
        translationJob = null
        activeJob.cancel()
        setStatus(SubtitleTranslationStatus.Idle)
        CineVaultToast.show(context, "Translation stopped")
    }
}
