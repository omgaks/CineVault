package com.sole.cinevault.subtitles

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

class SubtitleTranslationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getCurrentVideoPath: () -> String,
    private val resolveActiveSubtitle: () -> SubtitleSourceResolver.Resolved?,
    private val getStatus: () -> SubtitleTranslationStatus,
    private val setStatus: (SubtitleTranslationStatus) -> Unit,
    private val onSubtitleReady: (GeneratedSubtitleFile, String) -> Unit,
    private val onGeneratedLibraryChanged: () -> Unit,
) {
    private var translationJob: Job? = null
    private var translationGeneration = 0L

    fun translateActive(target: SubtitleTranslationEngine.SupportedLanguage) {
        if (translationJob?.isActive == true) return

        val generation = ++translationGeneration

        val source = resolveActiveSubtitle()
        if (source == null) {
            val reason =
                "No readable external subtitle is active. Load a Subtitle Studio download, local SRT, or generated subtitle first."
            setStatus(SubtitleTranslationStatus.Failed(reason))
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            return
        }

        val knownSource =
            source.language?.let(SubtitleTranslationEngine::mlKitCodeForWhisperLanguage)

        setStatus(SubtitleTranslationStatus.Translating("Starting", 0))
        val videoPath = getCurrentVideoPath()

        translationJob = scope.launch {
            try {
                val srtText = withContext(Dispatchers.IO) {
                    readTextFromUri(context, source.uri)
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
                        Toast.makeText(
                            context,
                            "Translation failed: ${result.reason}",
                            Toast.LENGTH_LONG,
                        ).show()
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
                    Toast.makeText(context, "Translation stopped", Toast.LENGTH_SHORT).show()
                }
            } catch (oom: OutOfMemoryError) {
                setStatus(
                    SubtitleTranslationStatus.Failed(
                        "Translation ran out of available memory. Try again after closing other apps."
                    )
                )
                Toast.makeText(
                    context,
                    "Translation stopped: not enough memory",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (t: Throwable) {
                val detail = t.message
                    ?.takeIf { it.isNotBlank() }
                    ?: t.javaClass.simpleName

                setStatus(
                    SubtitleTranslationStatus.Failed(
                        "Translation failed safely: $detail"
                    )
                )
                Toast.makeText(
                    context,
                    "Translation failed: $detail",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                if (generation == translationGeneration) {
                    translationJob = null
                }
            }
        }
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
        Toast.makeText(context, "Translation stopped", Toast.LENGTH_SHORT).show()
    }
}
