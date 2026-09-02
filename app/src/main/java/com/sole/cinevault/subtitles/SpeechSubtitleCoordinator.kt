package com.sole.cinevault.subtitles

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class SpeechSubtitleStatus {
    object Idle : SpeechSubtitleStatus()
    data class DownloadingModel(val fileName: String, val percent: Int) : SpeechSubtitleStatus()
    data class Generating(val phase: String, val percent: Int) : SpeechSubtitleStatus()
    data class Ready(val uri: Uri, val detectedLanguage: String?, val cueCount: Int) : SpeechSubtitleStatus()
    data class Failed(val reason: String) : SpeechSubtitleStatus()
}

class SpeechSubtitleCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val getCurrentVideoPath: () -> String,
    private val getStatus: () -> SpeechSubtitleStatus,
    private val setStatus: (SpeechSubtitleStatus) -> Unit,
    private val onSubtitleReady: (GeneratedSubtitleFile, String?) -> Unit,
    private val onGeneratedLibraryChanged: () -> Unit,
) {
    private var generationJob: Job? = null
    private var downloadJob: Job? = null

    fun downloadModel() {
        if (downloadJob?.isActive == true || generationJob?.isActive == true) return

        setStatus(SpeechSubtitleStatus.DownloadingModel("Starting", 0))
        downloadJob = scope.launch {
            try {
                when (
                    val result = WhisperModelManager.downloadStandardModel(context) { progress ->
                        setStatus(
                            SpeechSubtitleStatus.DownloadingModel(
                                progress.fileName,
                                progress.percent,
                            )
                        )
                    }
                ) {
                    WhisperModelManager.DownloadResult.Success -> {
                        setStatus(SpeechSubtitleStatus.Idle)
                        Toast.makeText(
                            context,
                            "${WhisperModelManager.modelDisplayName()} is ready",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    is WhisperModelManager.DownloadResult.Failed -> {
                        setStatus(SpeechSubtitleStatus.Failed(result.reason))
                        Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: CancellationException) {
                setStatus(SpeechSubtitleStatus.Idle)
            } finally {
                downloadJob = null
            }
        }
    }

    fun generateSubtitles() {
        if (generationJob?.isActive == true || downloadJob?.isActive == true) return

        val videoPath = getCurrentVideoPath()

        if (!supportsGenerationSource(videoPath)) {
            val reason =
                "Speech recognition currently supports local files and content:// videos only."
            setStatus(SpeechSubtitleStatus.Failed(reason))
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            return
        }

        if (!WhisperModelManager.isModelReady(context)) {
            setStatus(
                SpeechSubtitleStatus.Failed(
                    WhisperModelManager.setupInstructions(context)
                )
            )
            return
        }

        setStatus(SpeechSubtitleStatus.Generating("Starting", 0))

        val videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
        val audioLang = exoPlayer.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group ->
                (0 until group.length)
                    .firstOrNull { group.isTrackSelected(it) }
                    ?.let { index -> group.getTrackFormat(index).language }
            }

        generationJob = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    SubtitleGenerationEngine.generate(
                        context = context,
                        filePath = videoPath,
                        trackLanguage = audioLang,
                        videoDurationMs = videoDurationMs,
                        onProgress = { progress ->
                            setStatus(
                                SpeechSubtitleStatus.Generating(
                                    progress.phase,
                                    progress.percent,
                                )
                            )
                        }
                    )
                }

                when (result) {
                    is SubtitleGenerationEngine.Result.Failed -> {
                        setStatus(SpeechSubtitleStatus.Failed(result.reason))
                        Toast.makeText(
                            context,
                            "Speech recognition failed: ${result.reason}",
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
                                SpeechSubtitleStatus.Failed(
                                    "Recognition finished but the subtitle file couldn't be saved."
                                )
                            )
                            return@launch
                        }

                        val cueCount = generated.cueCount.coerceAtLeast(result.cueCount)
                        setStatus(
                            SpeechSubtitleStatus.Ready(
                                uri = generated.uri,
                                detectedLanguage = result.detectedLanguage,
                                cueCount = cueCount,
                            )
                        )
                        onGeneratedLibraryChanged()
                        onSubtitleReady(generated, result.detectedLanguage)

                        Toast.makeText(
                            context,
                            "Generated $cueCount subtitle ${if (cueCount == 1) "line" else "lines"}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } catch (_: CancellationException) {
                setStatus(SpeechSubtitleStatus.Idle)
                Toast.makeText(context, "Transcription stopped", Toast.LENGTH_SHORT).show()
            } finally {
                generationJob = null
            }
        }
    }

    fun cancelTranscription() {
        if (generationJob?.isActive == true) {
            generationJob?.cancel()
        }
    }

    fun cancelModelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
        }
    }

    private fun supportsGenerationSource(path: String): Boolean {
        if (path.startsWith("smb://", ignoreCase = true)) return false
        if (
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            path.startsWith("rtsp://", ignoreCase = true)
        ) return false

        return path.startsWith("content://", ignoreCase = true) || File(path).exists()
    }
}
