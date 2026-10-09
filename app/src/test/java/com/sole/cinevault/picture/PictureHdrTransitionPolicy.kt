package com.sole.cinevault.picture

/** Pure transition policy. Does not mutate Media3 effects or claim device verification. */
object PictureHdrTransitionPolicy {
    enum class Action { KEEP_SDR, KEEP_PASSTHROUGH, REMOVE_SDR_EFFECT_AND_REPREPARE }

    fun decide(
        route: PictureHdrPolicy.Route,
        sdrEffectInstalled: Boolean,
    ): Action = when {
        route == PictureHdrPolicy.Route.HDR_PASSTHROUGH && sdrEffectInstalled ->
            Action.REMOVE_SDR_EFFECT_AND_REPREPARE
        route == PictureHdrPolicy.Route.HDR_PASSTHROUGH -> Action.KEEP_PASSTHROUGH
        else -> Action.KEEP_SDR
    }
}
