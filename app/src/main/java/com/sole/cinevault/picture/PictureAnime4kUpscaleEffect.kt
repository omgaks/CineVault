package com.sole.cinevault.picture

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Experimental output-resizing pass for Anime4K integration.
 *
 * Media3 GL effects may return a larger output Size from configure(), which is
 * genuine frame resizing rather than relabeling sharpened source pixels.
 * This renderer is intentionally not connected to playback until output
 * surface negotiation, HDR routing and device performance have been verified.
 */
@OptIn(UnstableApi::class)
class PictureAnime4kUpscaleEffect(
    private val targetWidth: Int,
    private val targetHeight: Int,
) : GlEffect {
    init {
        require(targetWidth > 0 && targetHeight > 0)
        require(targetWidth % 2 == 0 && targetHeight % 2 == 0)
        require(targetWidth <= 3840 && targetHeight <= 2160)
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("HDR Anime4K upscale not yet validated")
        return Anime4kUpscaleProgram(targetWidth, targetHeight)
    }
}

@OptIn(UnstableApi::class)
private class Anime4kUpscaleProgram(
    private val targetWidth: Int,
    private val targetHeight: Int,
) : BaseGlShaderProgram(false, 1) {
    private val glProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    private var sourceTexel = floatArrayOf(1f / 1920f, 1f / 1080f)

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth <= 0 || inputHeight <= 0) {
            throw VideoFrameProcessingException("Invalid source dimensions")
        }
        sourceTexel = floatArrayOf(1f / inputWidth, 1f / inputHeight)
        return Size(targetWidth, targetHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexel", sourceTexel)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """
        // Four-tap reconstruction is a baseline only; the licensed Anime4K
        // shader stages must be adapted and validated separately.
        const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 uv = vTexSamplingCoord;
                vec3 center = texture2D(uTexSampler, uv).rgb;
                vec3 north = texture2D(uTexSampler, uv + vec2(0.0, -uTexel.y)).rgb;
                vec3 south = texture2D(uTexSampler, uv + vec2(0.0, uTexel.y)).rgb;
                vec3 west = texture2D(uTexSampler, uv + vec2(-uTexel.x, 0.0)).rgb;
                vec3 east = texture2D(uTexSampler, uv + vec2(uTexel.x, 0.0)).rgb;
                vec3 horizontal = (west + east) * 0.5;
                vec3 vertical = (north + south) * 0.5;
                float edgeH = length(west - east);
                float edgeV = length(north - south);
                float weightH = edgeH / (edgeH + edgeV + 0.0001);
                vec3 smooth = mix(horizontal, vertical, weightH);
                float localContrast = max(max(length(center - north), length(center - south)),
                                          max(length(center - west), length(center - east)));
                float lineMask = smoothstep(0.06, 0.24, localContrast);
                vec3 reconstructed = clamp(center + (center - smooth) * (0.10 * lineMask), 0.0, 1.0);
                gl_FragColor = vec4(reconstructed, 1.0);
            }
        """
    }
}
