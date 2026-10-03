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
 * Live shader values. The GL thread reads [current] on every frame, so slider moves and
 * hold-to-compare take effect instantly with no effect-pipeline rebuild (no black blip).
 */
class PictureLiveParams {
    @Volatile
    var current: PictureShaderParams = PictureShaderParams.OFF
}

/**
 * One merged GPU pass: gradient smoothing (deband) -> CAS-style adaptive sharpen ->
 * skin-safe vibrance -> dither / optional film grain.
 *
 * Works in Media3's default SDR working space (BT.709 primaries, gamma-encoded), which is
 * what CAS and dithering expect. HDR input is refused; the controller never attaches this
 * effect to HDR video.
 */
@OptIn(UnstableApi::class)
class PictureEnhanceEffect(private val live: PictureLiveParams) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) {
            throw VideoFrameProcessingException("Picture enhancement does not support HDR video")
        }
        return PictureShaderProgram(live)
    }
}

@OptIn(UnstableApi::class)
private class PictureShaderProgram(
    private val live: PictureLiveParams,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram = try {
        GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    private var texel = floatArrayOf(1f / 1280f, 1f / 720f)

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texel = floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val p = live.current
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.setFloatsUniform("uTexel", texel)
            glProgram.setFloatUniform("uAmount", p.amount)
            glProgram.setFloatUniform("uSharpen", p.sharpen)
            glProgram.setFloatUniform("uDeband", p.deband)
            glProgram.setFloatUniform("uColour", p.colour)
            glProgram.setFloatUniform("uGrain", p.grain)
            // Changes every frame so grain / dither never sits still.
            glProgram.setFloatUniform(
                "uSeed",
                ((presentationTimeUs / 1000L) % 997L).toFloat() / 997f,
            )
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
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
        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // Sharpen step adapted from AMD FidelityFX CAS (MIT licence).
        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uAmount;
uniform float uSharpen;
uniform float uDeband;
uniform float uColour;
uniform float uGrain;
uniform float uSeed;
varying vec2 vTexSamplingCoord;

float rand(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 e0 = texture2D(uTexSampler, uv).rgb;
  if (uAmount <= 0.002) {
    gl_FragColor = vec4(e0, 1.0);
    return;
  }
  vec3 e = e0;

  // 1. Gradient smoothing: only where the neighbourhood is a near-flat gradient.
  if (uDeband > 0.01) {
    vec2 seedXY = gl_FragCoord.xy + vec2(uSeed * 61.0, uSeed * 29.0);
    float ang = rand(seedXY) * 6.2831853;
    float rad = 2.0 + rand(seedXY.yx + 7.7) * (4.0 + 10.0 * uDeband);
    vec2 dPx = vec2(cos(ang), sin(ang)) * rad;
    vec2 d1 = dPx * uTexel;
    vec2 d2 = vec2(-dPx.y, dPx.x) * uTexel;
    vec3 s1 = texture2D(uTexSampler, uv + d1).rgb;
    vec3 s2 = texture2D(uTexSampler, uv - d1).rgb;
    vec3 s3 = texture2D(uTexSampler, uv + d2).rgb;
    vec3 s4 = texture2D(uTexSampler, uv - d2).rgb;
    vec3 diff = max(max(abs(s1 - e0), abs(s2 - e0)), max(abs(s3 - e0), abs(s4 - e0)));
    float worst = max(diff.r, max(diff.g, diff.b));
    float thr = mix(0.006, 0.022, uDeband);
    float flatness = 1.0 - smoothstep(thr * 0.5, thr, worst);
    vec3 avg = (s1 + s2 + s3 + s4 + e0) * 0.2;
    e = mix(e0, avg, flatness);
  }

  // 2. Contrast-adaptive sharpen (less on already-contrasty edges, so no halos).
  vec3 b = texture2D(uTexSampler, uv + vec2(0.0, -uTexel.y)).rgb;
  vec3 d = texture2D(uTexSampler, uv + vec2(-uTexel.x, 0.0)).rgb;
  vec3 f = texture2D(uTexSampler, uv + vec2(uTexel.x, 0.0)).rgb;
  vec3 h = texture2D(uTexSampler, uv + vec2(0.0, uTexel.y)).rgb;
  vec3 mn = min(min(min(d, e), min(f, b)), h);
  vec3 mx = max(max(max(d, e), max(f, b)), h);
  vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
  float peak = -1.0 / mix(8.0, 5.0, uSharpen);
  vec3 w = amp * peak;
  vec3 sharpened = clamp((b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w), 0.0, 1.0);
  vec3 col = mix(e, sharpened, uSharpen);

  // 3. Vibrance: lifts muted colours most, leaves skin tones and black alone.
  float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
  float mxc = max(col.r, max(col.g, col.b));
  float mnc = min(col.r, min(col.g, col.b));
  float chroma = mxc - mnc;
  float redMax = step(col.g, col.r) * step(col.b, col.r);
  float hueT = (col.g - col.b) / max(chroma, 0.0001);
  float skin = redMax
    * smoothstep(0.10, 0.28, hueT) * (1.0 - smoothstep(0.62, 0.90, hueT))
    * smoothstep(0.05, 0.14, chroma) * (1.0 - smoothstep(0.50, 0.70, chroma));
  float vib = uColour * (1.0 - chroma) * (1.0 - 0.75 * skin);
  col = clamp(mix(vec3(luma), col, 1.0 + 0.6 * vib), 0.0, 1.0);

  // 4. Dither (hides banding steps) + optional film grain. Masked off in near-black
  //    so letterbox bars and OLED blacks stay perfectly clean.
  float lumaOut = dot(col, vec3(0.2126, 0.7152, 0.0722));
  vec2 nSeed = gl_FragCoord.xy + vec2(uSeed * 113.0, uSeed * 71.0);
  float n = rand(nSeed) + rand(nSeed * 1.37 + 17.0) - 1.0;
  float mask = smoothstep(0.03, 0.12, lumaOut) * (1.0 - 0.5 * smoothstep(0.85, 1.0, lumaOut));
  float noiseAmp = uDeband * (1.0 / 255.0) + uGrain * 0.035;
  col += n * noiseAmp * mask;

  gl_FragColor = vec4(mix(e0, clamp(col, 0.0, 1.0), uAmount), 1.0);
}
"""
    }
}
