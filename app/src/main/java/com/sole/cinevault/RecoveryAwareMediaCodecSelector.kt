package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * MediaCodec selector used by Playback Resilience.
 *
 * NORMAL/HARDWARE mode preserves Media3's normal decoder ordering.
 *
 * SOFTWARE mode only filters VIDEO decoder queries to software-only codecs.
 * Audio queries are deliberately left untouched so switching the video rescue
 * path cannot accidentally remove a working AC3/AAC/etc audio decoder.
 *
 * The selector is mutable on purpose: ExoPlayer asks it again when a renderer
 * is re-prepared after a decoder failure, allowing CineVault to switch the
 * same player instance to the platform's CPU software video decoder.
 */
@OptIn(UnstableApi::class)
internal class RecoveryAwareMediaCodecSelector(
    private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT,
) : MediaCodecSelector {

    @Volatile
    var engineMode: PlaybackEngineMode = PlaybackEngineMode.HARDWARE

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val decoderInfos = delegate.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )

        if (
            engineMode != PlaybackEngineMode.SOFTWARE ||
            !mimeType.startsWith("video/", ignoreCase = true)
        ) {
            return decoderInfos
        }

        return decoderInfos.filter { it.softwareOnly }
    }
}
