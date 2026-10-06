package com.sole.cinevault.picture

/**
 * P6 Movie Engine closure policy.
 *
 * Live-action restoration is deliberately bounded. Resolution changes recovery strength only;
 * it never changes semantic content routing. The same single Media3 Picture effect is retained.
 */
object PictureMovieEnginePolicy {
    data class Params(
        val enabled:Float,
        val detailRecovery:Float,
        val textureProtection:Float,
        val grainProtection:Float,
        val skinProtection:Float,
        val haloGuard:Float,
        val chromaGuard:Float,
        val sharpenCeiling:Float,
        val structureFloor:Float,
        val textureCeiling:Float,
        val faceRecoveryScale:Float,
    )

    fun forState(content:PictureContent,intensity:Float,sourceHeight:Int=1080):Params {
        if(content!=PictureContent.FILM)return disabled()

        val safeIntensity=intensity.coerceIn(0f,1f)
        val safeHeight=sourceHeight.coerceAtLeast(1)
        val quality=when {
            safeIntensity<.72f -> .52f
            safeIntensity>=.96f -> 1f
            else -> .78f
        }
        val source=when {
            safeHeight<=576 -> 1f
            safeHeight<=720 -> .82f
            safeHeight<=1080 -> .58f
            else -> .30f
        }
        val clean=when {
            safeHeight<=576 -> 0f
            safeHeight<=720 -> .12f
            safeHeight<=1080 -> .28f
            else -> .48f
        }

        return Params(
            enabled=1f,
            detailRecovery=(.22f*quality*source).coerceIn(0f,.22f),
            textureProtection=(.84f+.12f*clean).coerceIn(.84f,.96f),
            grainProtection=(.86f+.10f*clean).coerceIn(.86f,.96f),
            skinProtection=.94f,
            haloGuard=(.86f+.10f*clean).coerceIn(.86f,.96f),
            chromaGuard=(.90f+.07f*clean).coerceIn(.90f,.97f),
            sharpenCeiling=(.26f-.08f*clean).coerceIn(.18f,.26f),
            structureFloor=(.010f+.006f*clean).coerceIn(.010f,.016f),
            textureCeiling=(.060f-.014f*clean).coerceIn(.046f,.060f),
            faceRecoveryScale=(.22f-.06f*clean).coerceIn(.16f,.22f),
        )
    }

    private fun disabled()=Params(
        enabled=0f,
        detailRecovery=0f,
        textureProtection=1f,
        grainProtection=1f,
        skinProtection=1f,
        haloGuard=1f,
        chromaGuard=1f,
        sharpenCeiling=0f,
        structureFloor=1f,
        textureCeiling=0f,
        faceRecoveryScale=0f,
    )
}
