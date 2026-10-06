package com.sole.cinevault.picture

/**
 * Semantic animation subtype used by P5.
 *
 * IMPORTANT: source resolution is intentionally absent. Resolution may tune processing
 * strength later, but it must never be used as proof that animation is 2D or CGI.
 */
enum class AnimationType {
    ANIME,
    CLASSIC_2D,
    MODERN_2D,
    CGI_3D,
    HYBRID,
    STOP_MOTION,
    STYLIZED,
    UNKNOWN,
}

enum class AnimationClassificationSource {
    TMDB_METADATA,
    LOCAL_SIGNALS,
    MIXED,
    NONE,
}

data class AnimationClassification(
    val type: AnimationType = AnimationType.UNKNOWN,
    val confidence: Float = 0f,
    val source: AnimationClassificationSource = AnimationClassificationSource.NONE,
    val evidence: List<String> = emptyList(),
    val version: Int = AnimationIntelligence.CLASSIFIER_VERSION,
)

object AnimationIntelligence {
    const val CLASSIFIER_VERSION = 1

    private val animeReleaseGroups = Regex(
        "\\[(subsplease|erai-raws|horriblesubs|judas|ember|animetime|anime time|asw|yameii|" +
            "sallysubs|ohys-raws|neo-raws|mtbb|df68|tsundere)\\]",
        RegexOption.IGNORE_CASE,
    )
    private val animeWord = Regex("\\banime\\b", RegexOption.IGNORE_CASE)

    private val romanisedAnimeTitles = Regex(
        "\\b(mononoke|nausicaa|totoro|ponyo|kiki'?s delivery service|spirited away|" +
            "howl'?s moving castle|castle in the sky|grave of the fireflies|the wind rises|" +
            "porco rosso|only yesterday|whisper of the heart|the cat returns|" +
            "tale of the princess kaguya|when marnie was there|akira|perfect blue|paprika|" +
            "ghost in the shell|your name|weathering with you|suzume|wolf children|" +
            "summer wars|a silent voice)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val stopMotionTerms = setOf(
        "stop motion", "stop-motion", "claymation", "clay animation",
        "puppet animation", "puppet animated",
    )
    private val cgiTerms = setOf(
        "computer animation", "computer-animated", "computer animated",
        "computer-generated imagery", "cgi animation", "3d animation", "3-d animation",
    )
    private val classic2dTerms = setOf(
        "traditional animation", "hand-drawn animation", "hand drawn animation",
        "cel animation", "hand-drawn", "hand drawn",
    )
    private val modern2dTerms = setOf(
        "2d animation", "2-d animation", "flash animation", "vector animation",
        "digital 2d animation",
    )
    private val hybridTerms = setOf(
        "hybrid animation", "2d and 3d", "2d/3d", "live action and animation",
        "live-action and animation", "live action animation",
    )
    private val stylizedTerms = setOf(
        "stylized animation", "stylised animation", "experimental animation",
        "rotoscope", "rotoscoping",
    )

    fun classify(
        fileName: String,
        title: String = fileName,
        genres: List<String>,
        originalLanguage: String? = null,
        keywords: List<String> = emptyList(),
    ): AnimationClassification {
        val normalizedGenres = genres.map { it.trim().lowercase() }
        val animationGenre = normalizedGenres.any { it == "animation" || it == "anime" }
        val explicitAnimeGenre = normalizedGenres.any { it == "anime" }

        // Known non-animation metadata wins over filename/script hints. This is essential
        // for Japanese live action and mirrors the P4 safety rule.
        if (genres.isNotEmpty() && !animationGenre) {
            return AnimationClassification(
                type = AnimationType.UNKNOWN,
                confidence = 1f,
                source = AnimationClassificationSource.TMDB_METADATA,
                evidence = listOf("TMDB genres do not identify this title as animation"),
            )
        }

        val localAnimeEvidence = mutableListOf<String>()
        if (animeReleaseGroups.containsMatchIn(fileName)) localAnimeEvidence += "anime release-group tag"
        if (animeWord.containsMatchIn(fileName)) localAnimeEvidence += "anime filename tag"
        if (containsJapaneseScript(fileName)) localAnimeEvidence += "Japanese script in filename"
        if (animationGenre && romanisedAnimeTitles.containsMatchIn("$title $fileName")) {
            localAnimeEvidence += "known romanised Japanese animation title"
        }

        val metadataAnimeEvidence = mutableListOf<String>()
        if (explicitAnimeGenre) metadataAnimeEvidence += "Anime genre"
        if (animationGenre && originalLanguage.equals("ja", ignoreCase = true)) {
            metadataAnimeEvidence += "Animation genre + Japanese original language"
        }

        if (metadataAnimeEvidence.isNotEmpty() || localAnimeEvidence.isNotEmpty()) {
            val source = sourceFor(metadataAnimeEvidence.isNotEmpty(), localAnimeEvidence.isNotEmpty())
            val confidence = when {
                explicitAnimeGenre -> 0.99f
                metadataAnimeEvidence.isNotEmpty() && localAnimeEvidence.isNotEmpty() -> 0.98f
                metadataAnimeEvidence.isNotEmpty() -> 0.96f
                else -> 0.90f
            }
            return AnimationClassification(
                AnimationType.ANIME, confidence, source,
                metadataAnimeEvidence + localAnimeEvidence,
            )
        }

        if (!animationGenre) return AnimationClassification()

        val normalizedKeywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        fun matching(terms: Set<String>): List<String> =
            normalizedKeywords.filter { keyword ->
                terms.any { term -> keyword == term || keyword.contains(term) }
            }.distinct()

        // Hybrid must precede 2D/3D because its evidence can contain both words.
        val candidates = listOf(
            Triple(AnimationType.HYBRID, matching(hybridTerms), 0.97f),
            Triple(AnimationType.STOP_MOTION, matching(stopMotionTerms), 0.98f),
            Triple(AnimationType.CGI_3D, matching(cgiTerms), 0.96f),
            Triple(AnimationType.CLASSIC_2D, matching(classic2dTerms), 0.96f),
            Triple(AnimationType.MODERN_2D, matching(modern2dTerms), 0.93f),
            Triple(AnimationType.STYLIZED, matching(stylizedTerms), 0.88f),
        )

        val winner = candidates.firstOrNull { it.second.isNotEmpty() }
        if (winner != null) {
            return AnimationClassification(
                type = winner.first,
                confidence = winner.third,
                source = AnimationClassificationSource.TMDB_METADATA,
                evidence = winner.second.map { "TMDB keyword: $it" },
            )
        }

        // Animation is known, but production technique is not. Never invent CGI/2D.
        return AnimationClassification(
            type = AnimationType.UNKNOWN,
            confidence = 0.55f,
            source = AnimationClassificationSource.TMDB_METADATA,
            evidence = listOf("Animation genre present; no reliable subtype evidence"),
        )
    }

    private fun containsJapaneseScript(value: String): Boolean =
        value.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' }

    private fun sourceFor(metadata: Boolean, local: Boolean): AnimationClassificationSource =
        when {
            metadata && local -> AnimationClassificationSource.MIXED
            metadata -> AnimationClassificationSource.TMDB_METADATA
            local -> AnimationClassificationSource.LOCAL_SIGNALS
            else -> AnimationClassificationSource.NONE
        }
}
