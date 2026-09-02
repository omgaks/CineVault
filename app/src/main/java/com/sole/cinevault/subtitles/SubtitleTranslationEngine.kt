package com.sole.cinevault.subtitles

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

object SubtitleTranslationEngine {

    data class Progress(val phase: String, val percent: Int)

    sealed class Result {
        data class Success(
            val srtText: String,
            val sourceLanguage: String,
        ) : Result()

        data class Failed(val reason: String) : Result()
    }

    data class SupportedLanguage(val label: String, val mlKitCode: String)

    val commonTargetLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage("English", TranslateLanguage.ENGLISH),
        SupportedLanguage("Hindi", TranslateLanguage.HINDI),
        SupportedLanguage("Spanish", TranslateLanguage.SPANISH),
        SupportedLanguage("French", TranslateLanguage.FRENCH),
        SupportedLanguage("German", TranslateLanguage.GERMAN),
        SupportedLanguage("Portuguese", TranslateLanguage.PORTUGUESE),
        SupportedLanguage("Japanese", TranslateLanguage.JAPANESE),
        SupportedLanguage("Korean", TranslateLanguage.KOREAN),
        SupportedLanguage("Chinese (Simplified)", TranslateLanguage.CHINESE),
        SupportedLanguage("Arabic", TranslateLanguage.ARABIC),
        SupportedLanguage("Russian", TranslateLanguage.RUSSIAN),
        SupportedLanguage("Italian", TranslateLanguage.ITALIAN),
        SupportedLanguage("Tamil", TranslateLanguage.TAMIL),
        SupportedLanguage("Telugu", TranslateLanguage.TELUGU),
        SupportedLanguage("Bengali", TranslateLanguage.BENGALI),
    )

    suspend fun translate(
        srtText: String,
        targetMlKitCode: String,
        sourceMlKitCode: String?,
        onProgress: (Progress) -> Unit,
    ): Result {
        val blocks = parseSrtBlocks(srtText)
        if (blocks.isEmpty()) {
            return Result.Failed("Couldn't parse this file as .srt — nothing to translate.")
        }

        onProgress(Progress("Detecting source language", 0))

        val sample = blocks
            .asSequence()
            .flatMap { it.lines.asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(40)
            .joinToString(" ")
            .take(4000)

        val source = sourceMlKitCode ?: detectSourceLanguage(sample)
            ?: return Result.Failed("Couldn't confidently identify the subtitle language.")

        if (source == targetMlKitCode) {
            return Result.Success(srtText, source)
        }

        currentCoroutineContext().ensureActive()

        // Translator.downloadModelIfNeeded() below is ML Kit's supported
        // model-readiness path. Do not duplicate it through
        // RemoteModelManager.getInstance(): that extra preflight can fail
        // inside ML Kit before a Translator exists on some release builds.
        onProgress(Progress("Starting translator", 0))

        val translator: Translator = try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(targetMlKitCode)
                .build()

            Translation.getClient(options)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return Result.Failed(
                "ML Kit couldn't create the translator (${diagnosticMessage(t)})."
            )
        }

        try {
            // This single documented Translator-level call owns model
            // download/readiness for the selected language pair.
            onProgress(Progress("Preparing translator", 0))
            try {
                translator
                    .downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .await()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                return Result.Failed(
                    "ML Kit translator preparation failed (${diagnosticMessage(t)})."
                )
            }

            val out = StringBuilder()

            blocks.forEachIndexed { i, block ->
                currentCoroutineContext().ensureActive()
                onProgress(
                    Progress(
                        "Translating",
                        (i * 100 / blocks.size).coerceIn(0, 99),
                    )
                )

                val originalText = block.lines.joinToString("\n")
                val translatedText =
                    if (originalText.isBlank()) {
                        originalText
                    } else {
                        try {
                            translator.translate(originalText).await()
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t

                            // Do NOT silently copy the English/original cue.
                            // One failed cue means the generated subtitle is not
                            // trustworthy, so stop and report the exact stage.
                            return Result.Failed(
                                "ML Kit translation failed at subtitle ${i + 1} of " +
                                    "${blocks.size} (${diagnosticMessage(t)})."
                            )
                        }
                    }

                if (block.index.isNotBlank()) {
                    out.append(block.index).append('\n')
                }
                out.append(normalizeTimingLine(block.timing)).append('\n')
                out.append(translatedText).append("\n\n")
            }

            onProgress(Progress("Done", 100))
            return Result.Success(
                srtText = out.toString().trim() + "\n",
                sourceLanguage = source,
            )
        } finally {
            try {
                translator.close()
            } catch (_: Throwable) {
                // Closing must never turn a completed/diagnostic translation
                // result into an app-level crash.
            }
        }
    }

    private suspend fun detectSourceLanguage(sample: String): String? {
        if (sample.isBlank()) return null

        val identifier = LanguageIdentification.getClient()
        return try {
            val code = identifier.identifyLanguage(sample).await()
            if (code == "und") null else mlKitCodeForLanguageTag(code)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        } finally {
            try {
                identifier.close()
            } catch (_: Throwable) {
                // Diagnostic cleanup only.
            }
        }
    }

    fun mlKitCodeForWhisperLanguage(code: String): String? =
        mlKitCodeForLanguageTag(code)

    private fun mlKitCodeForLanguageTag(tag: String): String? {
        return when (tag.lowercase().substringBefore('-')) {
            "en" -> TranslateLanguage.ENGLISH
            "hi" -> TranslateLanguage.HINDI
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "zh" -> TranslateLanguage.CHINESE
            "ar" -> TranslateLanguage.ARABIC
            "ru" -> TranslateLanguage.RUSSIAN
            "it" -> TranslateLanguage.ITALIAN
            "ta" -> TranslateLanguage.TAMIL
            "te" -> TranslateLanguage.TELUGU
            "bn" -> TranslateLanguage.BENGALI
            else -> null
        }
    }

    private fun diagnosticMessage(t: Throwable): String {
        val type = t.javaClass.simpleName.ifBlank { t.javaClass.name }
        val message = t.message?.trim().orEmpty()
        return if (message.isBlank()) type else "$type: $message"
    }
}
