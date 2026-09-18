package com.sole.cinevault.playback.rescue.video

/**
 * Owns the currently attached FFmpeg video output target.
 *
 * B8 keeps output ownership independent from decoder-session ownership. This
 * lets later slices bind Android Surface/native rendering without putting
 * platform rendering state into VideoPlayerScreen.
 */
class FfmpegVideoOutputBinding : FfmpegVideoOutput {

    override var target: FfmpegVideoOutputTarget? = null
        private set

    override fun attach(target: FfmpegVideoOutputTarget) {
        this.target = target
    }

    override fun detach() {
        target = null
    }
}
