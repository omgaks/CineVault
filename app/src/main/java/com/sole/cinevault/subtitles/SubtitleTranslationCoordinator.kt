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

    fun translateActive(target: SubtitleTranslationEngine.SupportedLanguage) {
        if (translationJob?.isActive == true) return

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

                val result = withContext(Dispatchers.Default) {
                    SubtitleTranslationEngine.translate(
                        srtText = srtText,
                        targetMlKitCode = target.mlKitCode,
                        sourceMlKitCode = knownSource,
                        onProgress = { progress ->
                            setStatus(
                                SubtitleTranslationStatus.Translating(
                                    progress.phase,
                                    progress.percent,
                                )
                            )
                        }
                    )
                }

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
                        Toast.makeText(
                            context,
                            "Translated to ${target.label}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } catch (_: CancellationException) {
                setStatus(SubtitleTranslationStatus.Idle)
                Toast.makeText(context, "Translation stopped", Toast.LENGTH_SHORT).show()
            } finally {
                translationJob = null
            }
        }
    }

    fun cancelTranslation() {
        if (translationJob?.isActive == true) translationJob?.cancel()
    }
}
