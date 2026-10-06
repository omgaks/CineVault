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

class PictureLiveParams {
    @Volatile var current: PictureShaderParams = PictureShaderParams.OFF
    @Volatile var frames: Long = 0L
}

/**
 * P3 Repair Engine 2.0.
 * Single GPU pass: edge-aware gradient repair -> adaptive sharpen ->
 * skin-safe vibrance -> temporal dither / optional grain.
 */
@OptIn(UnstableApi::class)
class PictureEnhanceEffect(private val live: PictureLiveParams) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("Picture enhancement does not support HDR video")
        return PictureShaderProgram(live)
    }
}

@OptIn(UnstableApi::class)
private class PictureShaderProgram(
    private val live: PictureLiveParams,
) : BaseGlShaderProgram(false, 1) {
    private val glProgram = try {
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
        texel = floatArrayOf(1f/inputWidth.coerceAtLeast(1), 1f/inputHeight.coerceAtLeast(1))
        return Size(inputWidth,inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val p=live.current
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler",inputTexId,0)
            glProgram.setFloatsUniform("uTexel",texel)
            glProgram.setFloatUniform("uAmount",p.amount)
            glProgram.setFloatUniform("uSharpen",p.sharpen)
            glProgram.setFloatUniform("uDeband",p.deband)
            glProgram.setFloatUniform("uColour",p.colour)
            glProgram.setFloatUniform("uGrain",p.grain)
            glProgram.setFloatUniform("uSplit",p.split)
            glProgram.setFloatUniform("uSeed",((presentationTimeUs/1000L)%997L).toFloat()/997f)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            live.frames=live.frames+1L
        } catch(e:GlUtil.GlException) {
            throw VideoFrameProcessingException(e,presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { glProgram.delete() } catch(e:GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main(){gl_Position=aFramePosition;vTexSamplingCoord=aFramePosition.xy*0.5+0.5;}
"""
        // Sharpen step remains based on AMD FidelityFX CAS (MIT).
        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uAmount,uSharpen,uDeband,uColour,uGrain,uSplit,uSeed;
varying vec2 vTexSamplingCoord;

float rand(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}
float luma709(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec2 chromaRG(vec3 c){float y=luma709(c);return vec2(c.r-y,c.b-y);}

void main(){
  vec2 uv=vTexSamplingCoord;
  vec3 e0=texture2D(uTexSampler,uv).rgb;
  if(uAmount<=0.002){gl_FragColor=vec4(e0,1.0);return;}
  vec3 e=e0;

  // P3-S1: edge-aware repair. Four taps preserve V1's broad cost class.
  if(uDeband>0.01){
    vec2 seedXY=gl_FragCoord.xy+vec2(uSeed*61.0,uSeed*29.0);
    float jitter=rand(seedXY)-0.5;
    // Conservative reach: roughly 1.1..5.1 px, versus V1's much wider random reach.
    float radiusPx=1.5+3.2*uDeband+0.8*jitter;
    vec2 dx=vec2(radiusPx*uTexel.x,0.0);
    vec2 dy=vec2(0.0,radiusPx*uTexel.y);
    vec3 l=texture2D(uTexSampler,uv-dx).rgb;
    vec3 r=texture2D(uTexSampler,uv+dx).rgb;
    vec3 t=texture2D(uTexSampler,uv-dy).rgb;
    vec3 b=texture2D(uTexSampler,uv+dy).rgb;

    float y0=luma709(e0),yl=luma709(l),yr=luma709(r),yt=luma709(t),yb=luma709(b);
    float gradH=abs(yr-yl),gradV=abs(yb-yt);
    float safeH=1.0/(0.001+gradH),safeV=1.0/(0.001+gradV);
    float safeSum=safeH+safeV; safeH/=safeSum; safeV/=safeSum;
    vec3 directional=((l+e0+r)/3.0)*safeH+((t+e0+b)/3.0)*safeV;

    float lumaRange=max(max(abs(yl-y0),abs(yr-y0)),max(abs(yt-y0),abs(yb-y0)));
    vec2 c0=chromaRG(e0);
    float chromaRange=max(
      max(length(chromaRG(l)-c0),length(chromaRG(r)-c0)),
      max(length(chromaRG(t)-c0),length(chromaRG(b)-c0))
    );
    float lap=abs((yl+yr+yt+yb)*0.25-y0);

    float lumaThr=mix(0.006,0.020,uDeband);
    float chromaThr=mix(0.008,0.026,uDeband);
    float detailThr=mix(0.0025,0.0090,uDeband);
    float lumaFlat=1.0-smoothstep(lumaThr*0.55,lumaThr,lumaRange);
    float chromaFlat=1.0-smoothstep(chromaThr*0.55,chromaThr,chromaRange);
    float detailSafe=1.0-smoothstep(detailThr*0.50,detailThr,lap);
    float repairMask=lumaFlat*chromaFlat*detailSafe;
    float repairStrength=repairMask*mix(0.18,0.62,uDeband);
    e=mix(e0,directional,repairStrength);
  }

  // Existing contrast-adaptive sharpen.
  vec3 sb=texture2D(uTexSampler,uv+vec2(0.0,-uTexel.y)).rgb;
  vec3 sd=texture2D(uTexSampler,uv+vec2(-uTexel.x,0.0)).rgb;
  vec3 sf=texture2D(uTexSampler,uv+vec2(uTexel.x,0.0)).rgb;
  vec3 sh=texture2D(uTexSampler,uv+vec2(0.0,uTexel.y)).rgb;
  vec3 mn=min(min(min(sd,e),min(sf,sb)),sh);
  vec3 mx=max(max(max(sd,e),max(sf,sb)),sh);
  vec3 amp=sqrt(clamp(min(mn,1.0-mx)/max(mx,vec3(0.0001)),0.0,1.0));
  float peak=-1.0/mix(9.0,4.0,uSharpen);
  vec3 w=amp*peak;
  vec3 col=clamp((sb*w+sd*w+sf*w+sh*w+e)/(1.0+4.0*w),0.0,1.0);

  // Existing skin-safe vibrance.
  float luma=luma709(col);
  float mxc=max(col.r,max(col.g,col.b)),mnc=min(col.r,min(col.g,col.b));
  float chroma=mxc-mnc;
  float redMax=step(col.g,col.r)*step(col.b,col.r);
  float hueT=(col.g-col.b)/max(chroma,0.0001);
  float skin=redMax*smoothstep(0.10,0.28,hueT)*(1.0-smoothstep(0.62,0.90,hueT))
    *smoothstep(0.05,0.14,chroma)*(1.0-smoothstep(0.50,0.70,chroma));
  float vib=uColour*(1.0-chroma)*(1.0-0.75*skin);
  col=clamp(mix(vec3(luma),col,1.0+1.6*vib),0.0,1.0);

  // Temporal triangular dither + optional film grain.
  float lumaOut=luma709(col);
  vec2 nSeed=gl_FragCoord.xy+vec2(uSeed*113.0,uSeed*71.0);
  float n=rand(nSeed)+rand(nSeed*1.37+17.0)-1.0;
  float mask=smoothstep(0.03,0.12,lumaOut)*(1.0-0.5*smoothstep(0.85,1.0,lumaOut));
  float noiseAmp=uDeband*(0.85/255.0)+uGrain*0.035;
  col+=n*noiseAmp*mask;

  vec3 outc=mix(e0,clamp(col,0.0,1.0),uAmount);
  if(uSplit>0.0){
    float sx=vTexSamplingCoord.x;
    if(sx>uSplit){outc=e0;}
    if(abs(sx-uSplit)<uTexel.x*1.5){outc=vec3(1.0,0.75,0.2);}
  }
  gl_FragColor=vec4(outc,1.0);
}
"""
    }
}
