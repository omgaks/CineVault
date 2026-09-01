package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class SubtitleGenerationStatus {
    object Idle : SubtitleGenerationStatus()
    data class DownloadingModel(val fileName: String, val percent: Int) : SubtitleGenerationStatus()
    data class Generating(val phase: String, val percent: Int) : SubtitleGenerationStatus()
    data class Translating(val phase: String, val percent: Int) : SubtitleGenerationStatus()
    data class Ready(
        val uri: Uri,
        val detectedLanguage: String?,
        val cueCount: Int,
    ) : SubtitleGenerationStatus()
    data class Failed(val reason: String) : SubtitleGenerationStatus()
}

class SubtitleGenerationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val getCurrentVideoPath: () -> String,
    private val getPrimarySubtitleUri: () -> Uri?,
    private val getStatus: () -> SubtitleGenerationStatus,
    private val setStatus: (SubtitleGenerationStatus) -> Unit,
    private val onSubtitleReady: (Uri) -> Unit,
    private val onGeneratedLibraryChanged: () -> Unit = {},
) {
    fun downloadModel() {
        if (getStatus() is SubtitleGenerationStatus.DownloadingModel) return
        setStatus(SubtitleGenerationStatus.DownloadingModel("Starting", 0))

        scope.launch {
            when (
                val result = WhisperModelManager.downloadStandardModel(context) { progress ->
                    setStatus(
                        SubtitleGenerationStatus.DownloadingModel(
                            progress.fileName,
                            progress.percent,
                        )
                    )
                }
            ) {
                WhisperModelManager.DownloadResult.Success -> {
                    setStatus(SubtitleGenerationStatus.Idle)
                    Toast.makeText(
                        context,
                        "${WhisperModelManager.modelDisplayName()} is ready",
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is WhisperModelManager.DownloadResult.Failed -> {
                    setStatus(SubtitleGenerationStatus.Failed(result.reason))
                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun generateSubtitles() {
        if (
            getStatus() is SubtitleGenerationStatus.Generating ||
            getStatus() is SubtitleGenerationStatus.DownloadingModel ||
            getStatus() is SubtitleGenerationStatus.Translating
        ) return

        val videoPath = getCurrentVideoPath()

        if (!supportsGenerationSource(videoPath)) {
            val reason =
                "AI subtitle generation currently supports local files and content:// videos only."
            setStatus(SubtitleGenerationStatus.Failed(reason))
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            return
        }

        if (!WhisperModelManager.isModelReady(context)) {
            setStatus(
                SubtitleGenerationStatus.Failed(
                    WhisperModelManager.setupInstructions(context)
                )
            )
            return
        }

        setStatus(SubtitleGenerationStatus.Generating("Starting", 0))

        val videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
        val audioLang = exoPlayer.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group ->
                (0 until group.length)
                    .firstOrNull { group.isTrackSelected(it) }
                    ?.let { index -> group.getTrackFormat(index).language }
            }

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                SubtitleGenerationEngine.generate(
                    context = context,
                    filePath = videoPath,
                    trackLanguage = audioLang,
                    videoDurationMs = videoDurationMs,
                    onProgress = { progress ->
                        setStatus(
                            SubtitleGenerationStatus.Generating(
                                progress.phase,
                                progress.percent,
                            )
                        )
                    }
                )
            }

            when (result) {
                is SubtitleGenerationEngine.Result.Failed -> {
                    setStatus(SubtitleGenerationStatus.Failed(result.reason))
                    Toast.makeText(
                        context,
                        "Subtitle generation failed: ${result.reason}",
                        Toast.LENGTH_LONG,
                    ).show()
                }

                is SubtitleGenerationEngine.Result.Success -> {
                    val generated = withContext(Dispatchers.IO) {
                        GeneratedSubtitleStore.write(
                            context = context,
                            videoPath = videoPath,
                            srtText = result.srtText,
                            labelSuffix = "ai-${result.detectedLanguage ?: "auto"}",
                        )
                    }

                    if (generated == null) {
                        setStatus(
                            SubtitleGenerationStatus.Failed(
                                "Generated ${result.cueCount} lines but couldn't save the file."
                            )
                        )
                        return@launch
                    }

                    setStatus(
                        SubtitleGenerationStatus.Ready(
                            uri = generated.uri,
                            detectedLanguage = result.detectedLanguage,
                            cueCount = generated.cueCount.coerceAtLeast(result.cueCount),
                        )
                    )
                    onGeneratedLibraryChanged()
                    onSubtitleReady(generated.uri)

                    Toast.makeText(
                        context,
                        "Generated ${generated.cueCount.coerceAtLeast(result.cueCount)} subtitle lines",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun translateActive(
        target: SubtitleTranslationEngine.SupportedLanguage,
    ) {
        if (
            getStatus() is SubtitleGenerationStatus.Translating ||
            getStatus() is SubtitleGenerationStatus.DownloadingModel ||
            getStatus() is SubtitleGenerationStatus.Generating
        ) return

        val primary = getPrimarySubtitleUri()
        if (primary == null) {
            Toast.makeText(
                context,
                "Generate or load a subtitle first, then translate it",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val knownSource =
            (getStatus() as? SubtitleGenerationStatus.Ready)
                ?.detectedLanguage
                ?.let(SubtitleTranslationEngine::mlKitCodeForWhisperLanguage)

        setStatus(SubtitleGenerationStatus.Translating("Starting", 0))
        val videoPath = getCurrentVideoPath()

        scope.launch {
            val srtText =
                withContext(Dispatchers.IO) {
                    readTextFromUri(context, primary)
                }

            if (srtText == null) {
                setStatus(
                    SubtitleGenerationStatus.Failed(
                        "Couldn't read the current subtitle file"
                    )
                )
                return@launch
            }

            val result =
                withContext(Dispatchers.Default) {
                    SubtitleTranslationEngine.translate(
                        srtText = srtText,
                        targetMlKitCode = target.mlKitCode,
                        sourceMlKitCode = knownSource,
                        onProgress = { progress ->
                            setStatus(
                                SubtitleGenerationStatus.Translating(
                                    progress.phase,
                                    progress.percent,
                                )
                            )
                        }
                    )
                }

            when (result) {
                is SubtitleTranslationEngine.Result.Failed -> {
                    setStatus(SubtitleGenerationStatus.Failed(result.reason))
                    Toast.makeText(
                        context,
                        "Translation failed: ${result.reason}",
                        Toast.LENGTH_LONG,
                    ).show()
                }

                is SubtitleTranslationEngine.Result.Success -> {
                    val generated =
                        withContext(Dispatchers.IO) {
                            GeneratedSubtitleStore.write(
                                context = context,
                                videoPath = videoPath,
                                srtText = result.srtText,
                                labelSuffix = "translated-${target.mlKitCode}",
                            )
                        }

                    if (generated == null) {
                        setStatus(
                            SubtitleGenerationStatus.Failed(
                                "Translated successfully but couldn't save the file."
                            )
                        )
                        return@launch
                    }

                    setStatus(
                        SubtitleGenerationStatus.Ready(
                            uri = generated.uri,
                            detectedLanguage = target.mlKitCode,
                            cueCount = generated.cueCount,
                        )
                    )
                    onGeneratedLibraryChanged()
                    onSubtitleReady(generated.uri)

                    Toast.makeText(
                        context,
                        "Translated to ${target.label}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun loadGeneratedSubtitle(
        generated: GeneratedSubtitleFile,
    ) {
        setStatus(
            SubtitleGenerationStatus.Ready(
                uri = generated.uri,
                detectedLanguage = null,
                cueCount = generated.cueCount,
            )
        )
        onSubtitleReady(generated.uri)
    }

    private fun supportsGenerationSource(
        path: String,
    ): Boolean {
        if (path.startsWith("smb://", ignoreCase = true)) return false

        if (
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            path.startsWith("rtsp://", ignoreCase = true)
        ) return false

        return path.startsWith("content://", ignoreCase = true) ||
            File(path).exists()
    }
}
