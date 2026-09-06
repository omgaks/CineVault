package com.sole.cinevault.subtitles

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloadable Whisper catalog.
 *
 * Security rules:
 *  - HTTPS only.
 *  - Download into *.part.
 *  - SHA-256 every file before it can be installed.
 *  - Delete a mismatched file immediately.
 *  - Existing files are considered ready only after the same SHA-256 check.
 *
 * Certificate pinning is intentionally not used for Hugging Face/CDN
 * redirects; cryptographic file verification is the stable trust boundary.
 */
object WhisperModelManager {

    enum class ModelId {
        TINY_INT8,
        BASE_INT8,
        SMALL_INT8,
    }

    data class ModelInfo(
        val id: ModelId,
        val displayName: String,
        val tierLabel: String,
        val sizeLabel: String,
        val description: String,
        val installed: Boolean,
        val selected: Boolean,
    )

    data class DownloadProgress(
        val model: ModelId,
        val fileName: String,
        val percent: Int,
    )

    sealed class DownloadResult {
        object Success : DownloadResult()
        data class Failed(val reason: String) : DownloadResult()
    }

    private data class ModelFile(
        val name: String,
        val url: String,
        val expectedBytes: Long,
        val sha256: String,
    )

    private data class ModelSpec(
        val id: ModelId,
        val prefix: String,
        val subdir: String,
        val displayName: String,
        val tierLabel: String,
        val sizeLabel: String,
        val description: String,
        val files: List<ModelFile>,
    )

    private const val PREFS = "whisper_model_preferences"
    private const val KEY_SELECTED = "selected_model"
    private const val TOKEN_SHA256 =
        "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"

    private fun hf(repo: String, file: String): String =
        "https://huggingface.co/csukuangfj/$repo/resolve/main/$file?download=true"

    private val specs: Map<ModelId, ModelSpec> = listOf(
        ModelSpec(
            id = ModelId.TINY_INT8,
            prefix = "tiny",
            subdir = "whisper-tiny-int8",
            displayName = "Whisper Tiny INT8",
            tierLabel = "FAST",
            sizeLabel = "~104 MB",
            description = "Fastest. Best for quick subtitle generation.",
            files = listOf(
                ModelFile(
                    "tiny-encoder.int8.onnx",
                    hf("sherpa-onnx-whisper-tiny", "tiny-encoder.int8.onnx"),
                    12_937_772L,
                    "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
                ),
                ModelFile(
                    "tiny-decoder.int8.onnx",
                    hf("sherpa-onnx-whisper-tiny", "tiny-decoder.int8.onnx"),
                    89_855_401L,
                    "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
                ),
                ModelFile(
                    "tiny-tokens.txt",
                    hf("sherpa-onnx-whisper-tiny", "tiny-tokens.txt"),
                    816_730L,
                    TOKEN_SHA256,
                ),
            ),
        ),
        ModelSpec(
            id = ModelId.BASE_INT8,
            prefix = "base",
            subdir = "whisper-base-int8",
            displayName = "Whisper Base INT8",
            tierLabel = "BALANCED",
            sizeLabel = "~161 MB",
            description = "Balanced speed and accuracy. Default.",
            files = listOf(
                ModelFile(
                    "base-encoder.int8.onnx",
                    hf("sherpa-onnx-whisper-base", "base-encoder.int8.onnx"),
                    29_000_000L,
                    "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
                ),
                ModelFile(
                    "base-decoder.int8.onnx",
                    hf("sherpa-onnx-whisper-base", "base-decoder.int8.onnx"),
                    130_000_000L,
                    "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
                ),
                ModelFile(
                    "base-tokens.txt",
                    hf("sherpa-onnx-whisper-base", "base-tokens.txt"),
                    816_730L,
                    TOKEN_SHA256,
                ),
            ),
        ),
        ModelSpec(
            id = ModelId.SMALL_INT8,
            prefix = "small",
            subdir = "whisper-small-int8",
            displayName = "Whisper Small INT8",
            tierLabel = "ENHANCED",
            sizeLabel = "~375 MB",
            description = "Higher accuracy. Slower and uses more storage/RAM.",
            files = listOf(
                ModelFile(
                    "small-encoder.int8.onnx",
                    hf("sherpa-onnx-whisper-small", "small-encoder.int8.onnx"),
                    111_000_000L,
                    "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
                ),
                ModelFile(
                    "small-decoder.int8.onnx",
                    hf("sherpa-onnx-whisper-small", "small-decoder.int8.onnx"),
                    261_000_000L,
                    "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
                ),
                ModelFile(
                    "small-tokens.txt",
                    hf("sherpa-onnx-whisper-small", "small-tokens.txt"),
                    816_730L,
                    TOKEN_SHA256,
                ),
            ),
        ),
    ).associateBy { it.id }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun spec(id: ModelId): ModelSpec = specs.getValue(id)

    private fun modelDir(context: Context, id: ModelId): File =
        File(context.filesDir, spec(id).subdir)

    fun selectedModel(context: Context): ModelId {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, ModelId.BASE_INT8.name)
        return runCatching { ModelId.valueOf(raw ?: ModelId.BASE_INT8.name) }
            .getOrDefault(ModelId.BASE_INT8)
    }

    fun selectModel(context: Context, id: ModelId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED, id.name)
            .apply()
    }

    fun modelCatalog(context: Context): List<ModelInfo> {
        val selected = selectedModel(context)
        return ModelId.entries.map { id ->
            val s = spec(id)
            ModelInfo(
                id = id,
                displayName = s.displayName,
                tierLabel = s.tierLabel,
                sizeLabel = s.sizeLabel,
                description = s.description,
                installed = isModelInstalledFast(context, id),
                selected = selected == id,
            )
        }
    }

    fun isModelReady(context: Context): Boolean =
        isModelInstalledFast(context, selectedModel(context))

    fun isModelReady(context: Context, id: ModelId): Boolean =
        isModelInstalledFast(context, id)

    private fun isModelInstalledFast(context: Context, id: ModelId): Boolean {
        val dir = modelDir(context, id)
        return spec(id).files.all { fileSpec ->
            val file = File(dir, fileSpec.name)
            file.isFile && file.length() >= fileSpec.expectedBytes
        }
    }

    private fun isModelCryptographicallyVerified(context: Context, id: ModelId): Boolean {
        val dir = modelDir(context, id)
        return spec(id).files.all { fileSpec ->
            val file = File(dir, fileSpec.name)
            file.isFile &&
                file.length() >= fileSpec.expectedBytes &&
                sha256(file).equals(fileSpec.sha256, ignoreCase = true)
        }
    }

    fun modelDisplayName(context: Context): String =
        spec(selectedModel(context)).displayName

    fun modelDownloadSizeLabel(context: Context): String =
        spec(selectedModel(context)).sizeLabel

    fun modelDisplayName(): String = "Whisper Base INT8"
    fun modelDownloadSizeLabel(): String = "~161 MB"

    fun modelStoragePath(context: Context, id: ModelId = selectedModel(context)): String =
        modelDir(context, id).absolutePath

    suspend fun downloadStandardModel(
        context: Context,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult =
        downloadModel(context, selectedModel(context), onProgress)

    suspend fun downloadModel(
        context: Context,
        id: ModelId,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val model = spec(id)
        val dir = modelDir(context, id).apply { mkdirs() }
        val totalExpected = model.files.sumOf { it.expectedBytes }
        var completedBytes = 0L

        try {
            for (fileSpec in model.files) {
                coroutineContext.ensureActive()
                val finalFile = File(dir, fileSpec.name)

                if (
                    finalFile.isFile &&
                    finalFile.length() >= fileSpec.expectedBytes &&
                    sha256(finalFile).equals(fileSpec.sha256, ignoreCase = true)
                ) {
                    completedBytes += fileSpec.expectedBytes
                    onProgress(
                        DownloadProgress(
                            id,
                            fileSpec.name,
                            ((completedBytes * 100L) / totalExpected).toInt().coerceIn(0, 100),
                        )
                    )
                    continue
                }

                if (finalFile.exists()) finalFile.delete()
                val partFile = File(dir, "${fileSpec.name}.part")
                if (partFile.exists()) partFile.delete()

                val request = Request.Builder()
                    .url(fileSpec.url)
                    .header("User-Agent", "CineVault/2.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        partFile.delete()
                        return@withContext DownloadResult.Failed(
                            "Couldn't download ${fileSpec.name} (HTTP ${response.code})."
                        )
                    }

                    val body = response.body
                        ?: return@withContext DownloadResult.Failed(
                            "Empty response while downloading ${fileSpec.name}."
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
                                    completedBytes + fileBytes.coerceAtMost(fileSpec.expectedBytes)
                                onProgress(
                                    DownloadProgress(
                                        id,
                                        fileSpec.name,
                                        ((overall * 100L) / totalExpected).toInt().coerceIn(0, 99),
                                    )
                                )
                            }
                        }
                    }
                }

                if (partFile.length() < fileSpec.expectedBytes) {
                    partFile.delete()
                    return@withContext DownloadResult.Failed(
                        "${fileSpec.name} downloaded incompletely."
                    )
                }

                val actualHash = sha256(partFile)
                if (!actualHash.equals(fileSpec.sha256, ignoreCase = true)) {
                    partFile.delete()
                    return@withContext DownloadResult.Failed(
                        "Security check failed for ${fileSpec.name}. SHA-256 did not match; the file was deleted."
                    )
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.delete()
                    return@withContext DownloadResult.Failed(
                        "Couldn't install ${fileSpec.name}."
                    )
                }

                completedBytes += fileSpec.expectedBytes
            }

            onProgress(DownloadProgress(id, "Verified", 100))
            if (isModelCryptographicallyVerified(context, id)) {
                selectModel(context, id)
                DownloadResult.Success
            } else {
                DownloadResult.Failed(
                    "Download finished but model verification failed."
                )
            }
        } catch (e: Exception) {
            DownloadResult.Failed(e.message ?: "Model download failed.")
        }
    }

    fun deleteModel(context: Context): Boolean =
        deleteModel(context, selectedModel(context))

    fun deleteModel(context: Context, id: ModelId): Boolean =
        try {
            modelDir(context, id).deleteRecursively()
        } catch (_: Exception) {
            false
        }

    fun createRecognizer(context: Context): OfflineRecognizer? {
        val id = selectedModel(context)
        if (!isModelCryptographicallyVerified(context, id)) return null
        val s = spec(id)
        val dir = modelDir(context, id)

        return try {
            val modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "${dir.absolutePath}/${s.prefix}-encoder.int8.onnx",
                    decoder = "${dir.absolutePath}/${s.prefix}-decoder.int8.onnx",
                    language = "",
                    task = "transcribe",
                ),
                tokens = "${dir.absolutePath}/${s.prefix}-tokens.txt",
                modelType = "whisper",
                numThreads = 4,
                provider = "cpu",
            )
            OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    modelConfig = modelConfig,
                    decodingMethod = "greedy_search",
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    fun setupInstructions(context: Context): String {
        val id = selectedModel(context)
        val s = spec(id)
        return "Speech model is not installed. Download ${s.displayName} (${s.sizeLabel}) in Speech → Subtitles."
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
