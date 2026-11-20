package com.sole.cinevault.glasses.stereo

/**
 * The person's own 3D choice for glasses playback (Settings → Glasses Mode).
 *
 *  - AUTO: trust the file name ("SBS", "3D-SBS" ...), the safe default.
 *  - OFF: always play flat 2D, even if the name says 3D.
 *  - SIDE_BY_SIDE: treat every film as side-by-side 3D. For 3D films whose file name has no
 *    marker. Top/Bottom is deliberately NOT offered: it cannot be shown correctly yet.
 */
internal enum class StereoModeChoice(val stored: String, val label: String) {
    AUTO("auto", "Auto"),
    OFF("off", "Off (flat)"),
    SIDE_BY_SIDE("sbs", "Side-by-side");

    /** What the playback policy should force, or null to leave detection alone. */
    fun toOverride(): StereoPlaybackMode? = when (this) {
        AUTO -> null
        OFF -> StereoPlaybackMode.NORMAL_2D
        SIDE_BY_SIDE -> StereoPlaybackMode.SIDE_BY_SIDE
    }

    companion object {
        fun fromStored(value: String?): StereoModeChoice =
            entries.firstOrNull { it.stored == value } ?: AUTO
    }
}

/** What to tell the viewer about the current film's 3D state, or null for plain 2D. */
internal data class StereoNotice(val title: String, val detail: String)

internal object StereoNoticeResolver {
    fun resolve(plan: ExternalStereoRenderPlan): StereoNotice? = when {
        plan.mode == StereoPlaybackMode.SIDE_BY_SIDE && plan.supportedByNativeSbsOutput ->
            StereoNotice("3D  -  Side-by-side", "Turn on 3D with the glasses' own buttons (see your glasses' manual) to see it in depth.")
        plan.mode == StereoPlaybackMode.TOP_BOTTOM && !plan.supportedByNativeSbsOutput ->
            StereoNotice("3D  -  Top/Bottom", "Not converted yet, so it plays flat (2D) instead of looking wrong.")
        else -> null
    }
}
