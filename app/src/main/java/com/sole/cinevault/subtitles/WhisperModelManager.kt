package com.sole.cinevault.subtitles

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.getWhisperBaseModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

object WhisperModelManager {

    data class DownloadProgress(val fileName: String, val percent: Int)

    sealed class DownloadResult {
        object Success : DownloadResult()
        data class Failed(val reason: String) : DownloadResult()
    }

    private data class ModelFile(
        val name: String,
        val url: String,
        val expectedBytes: Long,
    )

    private const val MODEL_SUBDIR = "whisper-base-int8"
    private const val ENCODER_FILE = "base-encoder.int8.onnx"
    private const val DECODER_FILE = "base-decoder.int8.onnx"
    private const val TOKENS_FILE = "base-tokens.txt"

    private val files = listOf(
        ModelFile(
            ENCODER_FILE,
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx?download=true",
            29_000_000L,
        ),
        ModelFile(
            DECODER_FILE,
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx?download=true",
            130_000_000L,
        ),
        ModelFile(
            TOKENS_FILE,
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt?download=true",
            700_000L,
        ),
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun modelDir(context: Context): File =
        File(context.filesDir, MODEL_SUBDIR)

    fun isModelReady(context: Context): Boolean {
        val dir = modelDir(context)
        return files.all { spec ->
            val file = File(dir, spec.name)
            file.exists() && file.length() >= spec.expectedBytes
        }
    }

    fun modelDisplayName(): String = "Whisper Base INT8"
    fun modelDownloadSizeLabel(): String = "~161 MB"
    fun modelStoragePath(context: Context): String = modelDir(context).absolutePath

    suspend fun downloadStandardModel(
        context: Context,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dir = modelDir(context).apply { mkdirs() }
        val totalExpected = files.sumOf { it.expectedBytes }
        var completedBytes = 0L

        try {
            for (spec in files) {
                coroutineContext.ensureActive()
                val finalFile = File(dir, spec.name)

                if (finalFile.exists() && finalFile.length() >= spec.expectedBytes) {
                    completedBytes += spec.expectedBytes
                    onProgress(
                        DownloadProgress(
                            spec.name,
                            ((completedBytes * 100L) / totalExpected).toInt().coerceIn(0, 100),
                        )
                    )
                    continue
                }

                val partFile = File(dir, "${spec.name}.part")
                if (partFile.exists()) partFile.delete()

                val request = Request.Builder()
                    .url(spec.url)
                    .header("User-Agent", "CineVault/2.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult.Failed(
                            "Couldn't download ${spec.name} (HTTP ${response.code})."
                        )
                    }

                    val body = response.body
                        ?: return@withContext DownloadResult.Failed(
                            "Empty response while downloading ${spec.name}."
                        )

                    body.byteStream().use { input ->
                        partFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var fileBytes = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                fileBytes += read
                                val overall =
                                    completedBytes + fileBytes.coerceAtMost(spec.expectedBytes)
                                onProgress(
                                    DownloadProgress(
                                        spec.name,
                                        ((overall * 100L) / totalExpected).toInt().coerceIn(0, 99),
                                    )
                                )
                            }
                        }
                    }
                }

                if (partFile.length() < spec.expectedBytes) {
                    partFile.delete()
                    return@withContext DownloadResult.Failed(
                        "${spec.name} downloaded incompletely. Check your connection and try again."
                    )
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.delete()
                    return@withContext DownloadResult.Failed("Couldn't install ${spec.name}.")
                }

                completedBytes += spec.expectedBytes
            }

            onProgress(DownloadProgress("Ready", 100))
            if (isModelReady(context)) DownloadResult.Success
            else DownloadResult.Failed(
                "The model download finished but one or more files failed verification."
            )
        } catch (e: Exception) {
            DownloadResult.Failed(e.message ?: "Model download failed.")
        }
    }

    fun deleteModel(context: Context): Boolean =
        try {
            modelDir(context).deleteRecursively()
        } catch (_: Exception) {
            false
        }

    fun createRecognizer(context: Context): OfflineRecognizer? {
        if (!isModelReady(context)) return null
        val dir = modelDir(context)
        return try {
            OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    modelConfig = getWhisperBaseModelConfig(dir.absolutePath),
                    decodingMethod = "greedy_search",
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    fun setupInstructions(context: Context): String =
        "AI subtitle model is not installed. Open AI Subtitles and download " +
            "${modelDisplayName()} (${modelDownloadSizeLabel()}). The model is stored separately from the APK in:\n" +
            modelStoragePath(context)
}
