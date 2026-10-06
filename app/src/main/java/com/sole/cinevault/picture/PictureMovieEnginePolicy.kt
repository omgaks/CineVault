package com.sole.cinevault.picture

/** P6-S3 live-action recovery policy. Resolution tunes strength only, never content type. */
object PictureMovieEnginePolicy {
    data class Params(
        val enabled:Float,val detailRecovery:Float,val textureProtection:Float,
        val grainProtection:Float,val skinProtection:Float,val haloGuard:Float,
        val chromaGuard:Float,val sharpenCeiling:Float,val structureFloor:Float,
        val textureCeiling:Float,val faceRecoveryScale:Float,
    )
    fun forState(content:PictureContent,intensity:Float,sourceHeight:Int=1080):Params {
        if(content!=PictureContent.FILM)return disabled()
        val quality=when{intensity<.72f->.52f;intensity>=.96f->1f;else->.78f}
        val source=when{sourceHeight<=576->1f;sourceHeight<=720->.82f;sourceHeight<=1080->.58f;else->.30f}
        val clean=when{sourceHeight<=576->0f;sourceHeight<=720->.12f;sourceHeight<=1080->.28f;else->.48f}
        return Params(
            1f,(.22f*quality*source).coerceIn(0f,.22f),
            (.84f+.12f*clean).coerceIn(.84f,.96f),(.86f+.10f*clean).coerceIn(.86f,.96f),
            .94f,(.86f+.10f*clean).coerceIn(.86f,.96f),(.90f+.07f*clean).coerceIn(.90f,.97f),
            (.26f-.08f*clean).coerceIn(.18f,.26f),
            (.010f+.006f*clean).coerceIn(.010f,.016f),
            (.060f-.014f*clean).coerceIn(.046f,.060f),
            (.22f-.06f*clean).coerceIn(.16f,.22f),
        )
    }
    private fun disabled()=Params(0f,0f,1f,1f,1f,1f,1f,0f,1f,0f,0f)
}
