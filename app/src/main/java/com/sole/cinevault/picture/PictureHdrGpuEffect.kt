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
 * P8-S5: isolated HDR floating-point GPU identity pass.
 *
 * This effect is deliberately NOT installed by PictureEnhanceController. HDR frame
 * processing must be validated on hardware before enabling this effect in playback.
 * Media3 is responsible for input/output transfer and gamut handling; shader values
 * are left unchanged (no SDR clamping, tone mapping, or colour-space conversion).
 */
@OptIn(UnstableApi::class)
class PictureHdrGpuEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (!useHdr) throw VideoFrameProcessingException("HDR GPU effect requires HDR frame processing")
        return PictureHdrIdentityProgram()
    }
}

@OptIn(UnstableApi::class)
private class PictureHdrIdentityProgram : BaseGlShaderProgram(true, 1) {
    private val program = try {
        GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size =
        Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """
        const val FRAGMENT_SHADER = """
            precision highp float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
            }
        """
    }
}
