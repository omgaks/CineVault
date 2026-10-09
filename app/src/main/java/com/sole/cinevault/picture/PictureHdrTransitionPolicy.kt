package com.sole.cinevault.picture

/** Pure policy; the player owner is responsible for safe effect removal. */
object PictureHdrTransitionPolicy {
    enum class Action { KEEP_SDR, KEEP_PASSTHROUGH, REMOVE_SDR_EFFECT_AND_REPREPARE }

    fun decide(route: PictureHdrPolicy.Route, sdrEffectInstalled: Boolean): Action =
        when (route) {
            PictureHdrPolicy.Route.SDR_ENHANCEMENT -> Action.KEEP_SDR
            PictureHdrPolicy.Route.HDR_PASSTHROUGH ->
                if (sdrEffectInstalled) Action.REMOVE_SDR_EFFECT_AND_REPREPARE
                else Action.KEEP_PASSTHROUGH
        }
}
