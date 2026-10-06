package com.sole.cinevault.picture

/**
 * Picks the likeliest content type from TMDB genres (when known) and the filename.
 *
 * P4 closure rule:
 * - Non-animation TMDB metadata always wins -> Film.
 * - Explicit Anime metadata/release-group/Japanese-script signals -> Anime.
 * - For animation titles whose local filename is an English/romanised release, use a
 *   conservative Japanese-animation title signal. This fixes classic anime films that TMDB
 *   exposes only as "Animation" without turning every Disney/Pixar/CGI title into Anime.
 * - Otherwise Animation stays Animation for the separate P5 engine.
 */
object PictureContentDetector {

    private val animeReleaseGroups = Regex(
        "\\[(subsplease|erai-raws|horriblesubs|judas|ember|animetime|anime time|asw|yameii|" +
            "sallysubs|ohys-raws|neo-raws|mtbb|df68|tsundere)\\]",
        RegexOption.IGNORE_CASE,
    )

    private val animeWord = Regex("\\banime\\b", RegexOption.IGNORE_CASE)

    /**
     * Conservative fallback for well-known Japanese animated films that are commonly stored
     * with plain English/romanised filenames, so neither Japanese script nor a fansub tag exists.
     *
     * Keep this intentionally narrow: it is only consulted when metadata already says Animation.
     * Generic words such as "princess", "castle", "ghost", "garden", etc. are deliberately absent.
     */
    private val japaneseAnimationTitleHint = Regex(
        "\\b(" +
            "mononoke|" +
            "nausicaa|" +
            "totoro|" +
            "ponyo|" +
            "kiki'?s delivery service|" +
            "spirited away|" +
            "howl'?s moving castle|" +
            "castle in the sky|" +
            "grave of the fireflies|" +
            "the wind rises|" +
            "porco rosso|" +
            "only yesterday|" +
            "whisper of the heart|" +
            "the cat returns|" +
            "tale of the princess kaguya|" +
            "when marnie was there|" +
            "akira|" +
            "perfect blue|" +
            "paprika|" +
            "ghost in the shell|" +
            "your name|" +
            "weathering with you|" +
            "suzume|" +
            "wolf children|" +
            "summer wars|" +
            "a silent voice" +
        ")\\b",
        RegexOption.IGNORE_CASE,
    )

    fun detect(fileName: String, genres: List<String>): PictureContent {
        val hasJapanese = fileName.any {
            it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF'
        }

        val explicitAnimeGenre = genres.any { it.equals("Anime", ignoreCase = true) }
        val animationGenre = genres.any {
            it.equals("Animation", ignoreCase = true) || it.equals("Anime", ignoreCase = true)
        }

        val strongAnimeHint =
            explicitAnimeGenre ||
                animeReleaseGroups.containsMatchIn(fileName) ||
                animeWord.containsMatchIn(fileName) ||
                hasJapanese

        val romanisedAnimeFilmHint =
            animationGenre && japaneseAnimationTitleHint.containsMatchIn(fileName)

        val result = when {
            // Protect live action, including Japanese live action.
            genres.isNotEmpty() && !animationGenre -> PictureContent.FILM

            strongAnimeHint || romanisedAnimeFilmHint -> PictureContent.ANIME

            // Keep western/CGI/general animation on the P5 path.
            animationGenre -> PictureContent.ANIMATION

            // Filename-only anime evidence is still useful when metadata has not arrived yet.
            strongAnimeHint -> PictureContent.ANIME

            else -> PictureContent.FILM
        }

        PictureAnimeRoutingPolicy.updateDetected(result)
        return result
    }
}
