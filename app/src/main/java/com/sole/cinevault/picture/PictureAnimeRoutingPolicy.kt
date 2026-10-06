package com.sole.cinevault.picture

/**
 * P4-S3 runtime routing for the Anime engine.
 *
 * AUTO remains persisted as AUTO. Detection is kept only as transient runtime state so the
 * shader receives Anime/Animation/Film without changing the user's saved content choice.
 */
object PictureAnimeRoutingPolicy {
    @Volatile private var detectedContent: PictureContent = PictureContent.FILM

    fun updateDetected(content: PictureContent) {
        detectedContent = content
    }

    fun currentDetected(): PictureContent = detectedContent

    fun resolve(
        selected: PictureContent,
        detected: PictureContent = detectedContent,
    ): PictureContent = PictureProfiles.resolveContent(selected, detected)

    fun shouldRunAnime(
        selected: PictureContent,
        detected: PictureContent = detectedContent,
    ): Boolean = resolve(selected, detected) == PictureContent.ANIME
}
