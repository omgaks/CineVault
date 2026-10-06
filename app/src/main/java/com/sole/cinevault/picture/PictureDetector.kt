package com.sole.cinevault.picture

/**
 * Picks the likeliest content type from TMDB genres (when known) and the filename.
 * Genres win: a title TMDB does not call Animation is treated as Film even if the
 * filename has Japanese characters (live-action Japanese cinema must not get the
 * anime profile).
 */
object PictureContentDetector {

    private val animeReleaseGroups = Regex(
        "\\[(subsplease|erai-raws|horriblesubs|judas|ember|animetime|anime time|asw|yameii|" +
            "sallysubs|ohys-raws|neo-raws|mtbb|df68|tsundere)\\]",
        RegexOption.IGNORE_CASE,
    )
    private val animeWord = Regex("\\banime\\b", RegexOption.IGNORE_CASE)

    fun detect(fileName: String, genres: List<String>): PictureContent {
        val hasJapanese = fileName.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' }
        val animeHint =
            animeReleaseGroups.containsMatchIn(fileName) ||
                animeWord.containsMatchIn(fileName) ||
                hasJapanese
        val animationGenre = genres.any {
            it.equals("Animation", ignoreCase = true) || it.equals("Anime", ignoreCase = true)
        }
        val result = when {
            genres.isNotEmpty() && !animationGenre -> PictureContent.FILM
            animationGenre && animeHint -> PictureContent.ANIME
            animationGenre -> PictureContent.ANIMATION
            animeHint -> PictureContent.ANIME
            else -> PictureContent.FILM
        }
        PictureAnimeRoutingPolicy.updateDetected(result)
        return result
    }
}
