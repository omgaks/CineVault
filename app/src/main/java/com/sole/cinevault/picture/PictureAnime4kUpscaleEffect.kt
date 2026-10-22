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
        // Adapted from bloc97/Anime4K v3.2 Upscale DoG x2 (MIT).
        // The upstream mpv version uses separate luma, Gaussian-X, Gaussian-Y
        // and apply stages. This GLES 2.0 single-pass adaptation recomputes the
        // same separable 5x5 Gaussian at each output pixel to avoid intermediate
        // textures; it is not a byte-for-byte port of the upstream pipeline.
        // Copyright (c) bloc97/Anime4K contributors; see bundled upstream GLSL.
        const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            varying vec2 vTexSamplingCoord;
            float lum(vec2 uv) {
                return dot(texture2D(uTexSampler, clamp(uv, 0.0, 1.0)).rgb,
                           vec3(0.299, 0.587, 0.114));
            }
            void main() {
                vec2 uv = vTexSamplingCoord;
                float center = lum(uv);
                float gaussian = 0.0;
                float localMin = center;
                float localMax = center;
                // Anime4K DoG's separable Gaussian weights, evaluated directly
                // as a 5x5 convolution for broad GLES 2.0 compatibility.
                for (int y = -2; y <= 2; ++y) {
                    float wy = (y == 0) ? 0.38774 : ((y == -1 || y == 1) ? 0.24477 : 0.06136);
                    for (int x = -2; x <= 2; ++x) {
                        float wx = (x == 0) ? 0.38774 : ((x == -1 || x == 1) ? 0.24477 : 0.06136);
                        float value = lum(uv + vec2(float(x), float(y)) * uTexel);
                        gaussian += value * wx * wy;
                        localMin = min(localMin, value);
                        localMax = max(localMax, value);
                    }
                }
                float dog = (center - gaussian) * 0.8;
                float adjusted = clamp(center + dog, localMin, localMax);
                vec4 source = texture2D(uTexSampler, uv);
                gl_FragColor = vec4(clamp(source.rgb + vec3(adjusted - center), 0.0, 1.0), source.a);
            }
        """
    }
}
