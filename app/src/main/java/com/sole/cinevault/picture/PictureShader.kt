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

/** P6 single-pass enhancement: P3 repair/chroma -> Anime/Animation or Movie recovery -> CAS -> vibrance -> dither/grain. */
@OptIn(UnstableApi::class)
class PictureEnhanceEffect(private val live: PictureLiveParams) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("Picture enhancement does not support HDR video")
        return PictureShaderProgram(live)
    }
}

@OptIn(UnstableApi::class)
private class PictureShaderProgram(private val live: PictureLiveParams) :
    BaseGlShaderProgram(false, 1) {

    private val glProgram = try { GlProgram(VERTEX_SHADER, FRAGMENT_SHADER) }
    catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

    private var texel = floatArrayOf(1f/1280f,1f/720f)
    private var sourceHeight = 720

    init {
        glProgram.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth:Int,inputHeight:Int):Size {
        texel=floatArrayOf(1f/inputWidth.coerceAtLeast(1),1f/inputHeight.coerceAtLeast(1))
        sourceHeight=inputHeight.coerceAtLeast(1)
        return Size(inputWidth,inputHeight)
    }

    override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
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
            val adaptive=PictureAdaptiveRepairPolicy.forState(p.content,p.amount)
            glProgram.setFloatUniform("uRepairScale",adaptive.repair)
            glProgram.setFloatUniform("uChromaScale",adaptive.chroma)
            glProgram.setFloatUniform("uSharpenGuard",adaptive.sharpenGuard)

            val anime=PictureAnimeEnginePolicy.forState(p.content,p.amount,sourceHeight)
            glProgram.setFloatUniform("uAnimeEnabled",anime.enabled)
            glProgram.setFloatUniform("uAnimeLine",anime.lineStrength)
            glProgram.setFloatUniform("uAnimeFlat",anime.flatProtection)
            glProgram.setFloatUniform("uAnimeHalo",anime.haloGuard)
            glProgram.setFloatUniform("uAnimeReconstruct",anime.reconstruction)
            glProgram.setFloatUniform("uAnimeDiagonal",anime.diagonalAssist)
            glProgram.setFloatUniform("uAnimeChromaGuard",anime.chromaEdgeGuard)

            val movie=PictureMovieEnginePolicy.forState(p.content,p.amount,sourceHeight)
            glProgram.setFloatUniform("uMovieEnabled",movie.enabled)
            glProgram.setFloatUniform("uMovieDetail",movie.detailRecovery)
            glProgram.setFloatUniform("uMovieTexture",movie.textureProtection)
            glProgram.setFloatUniform("uMovieGrainProtect",movie.grainProtection)
            glProgram.setFloatUniform("uMovieSkinProtect",movie.skinProtection)
            glProgram.setFloatUniform("uMovieHalo",movie.haloGuard)
            glProgram.setFloatUniform("uMovieChromaGuard",movie.chromaGuard)
            glProgram.setFloatUniform("uMovieSharpenCeiling",movie.sharpenCeiling)

            glProgram.setFloatUniform("uSeed",((presentationTimeUs/1000L)%997L).toFloat()/997f)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            live.frames++
        } catch(e:GlUtil.GlException){throw VideoFrameProcessingException(e,presentationTimeUs)}
    }

    override fun release(){super.release();try{glProgram.delete()}catch(e:GlUtil.GlException){
        throw VideoFrameProcessingException(e)
    }}

    private companion object {
        const val VERTEX_SHADER="""attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main(){gl_Position=aFramePosition;vTexSamplingCoord=aFramePosition.xy*0.5+0.5;}"""

        const val FRAGMENT_SHADER="""
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uAmount,uSharpen,uDeband,uColour,uGrain,uSplit,uSeed;
uniform float uRepairScale,uChromaScale,uSharpenGuard;
uniform float uAnimeEnabled,uAnimeLine,uAnimeFlat,uAnimeHalo;
uniform float uAnimeReconstruct,uAnimeDiagonal,uAnimeChromaGuard;
uniform float uMovieEnabled,uMovieDetail,uMovieTexture,uMovieGrainProtect;
uniform float uMovieSkinProtect,uMovieHalo,uMovieChromaGuard,uMovieSharpenCeiling;
varying vec2 vTexSamplingCoord;
float rand(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}
float luma709(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec2 chromaRG(vec3 c){float y=luma709(c);return vec2(c.r-y,c.b-y);}
vec3 fromLumaChroma(float y,vec2 c){
  float r=y+c.x,b=y+c.y;
  float g=(y-0.2126*r-0.0722*b)/0.7152;
  return vec3(r,g,b);
}
float skinMask(vec3 c){
 float mxc=max(c.r,max(c.g,c.b)),mnc=min(c.r,min(c.g,c.b));
 float chroma=mxc-mnc,redMax=step(c.g,c.r)*step(c.b,c.r);
 float hueT=(c.g-c.b)/max(chroma,0.0001);
 return redMax*smoothstep(0.10,0.28,hueT)*(1.0-smoothstep(0.62,0.90,hueT))
  *smoothstep(0.05,0.14,chroma)*(1.0-smoothstep(0.50,0.70,chroma));
}

void main(){
 vec2 uv=vTexSamplingCoord; vec3 e0=texture2D(uTexSampler,uv).rgb;
 if(uAmount<=0.002){gl_FragColor=vec4(e0,1.0);return;} vec3 e=e0;

 // P3 edge-aware gradient repair.
 if(uDeband>0.01){
  vec2 seedXY=gl_FragCoord.xy+vec2(uSeed*61.0,uSeed*29.0);
  float radiusPx=1.5+3.2*uDeband+0.8*(rand(seedXY)-0.5);
  vec2 dx=vec2(radiusPx*uTexel.x,0.0),dy=vec2(0.0,radiusPx*uTexel.y);
  vec3 l=texture2D(uTexSampler,uv-dx).rgb,r=texture2D(uTexSampler,uv+dx).rgb;
  vec3 t=texture2D(uTexSampler,uv-dy).rgb,b=texture2D(uTexSampler,uv+dy).rgb;
  float y0=luma709(e0),yl=luma709(l),yr=luma709(r),yt=luma709(t),yb=luma709(b);
  float gh=abs(yr-yl),gv=abs(yb-yt),wh=1.0/(0.001+gh),wv=1.0/(0.001+gv);
  float ws=wh+wv; wh/=ws; wv/=ws;
  vec3 directional=((l+e0+r)/3.0)*wh+((t+e0+b)/3.0)*wv;
  float lr=max(max(abs(yl-y0),abs(yr-y0)),max(abs(yt-y0),abs(yb-y0)));
  vec2 c0=chromaRG(e0);
  float cr=max(max(length(chromaRG(l)-c0),length(chromaRG(r)-c0)),
               max(length(chromaRG(t)-c0),length(chromaRG(b)-c0)));
  float lap=abs((yl+yr+yt+yb)*0.25-y0);
  float lf=1.0-smoothstep(mix(0.006,0.020,uDeband)*0.55,mix(0.006,0.020,uDeband),lr);
  float cf=1.0-smoothstep(mix(0.008,0.026,uDeband)*0.55,mix(0.008,0.026,uDeband),cr);
  float ds=1.0-smoothstep(mix(0.0025,0.009,uDeband)*0.5,mix(0.0025,0.009,uDeband),lap);
  e=mix(e0,directional,lf*cf*ds*mix(0.18,0.62,uDeband)*uRepairScale);
 }

 // P3 chroma reconstruction.
 if(uDeband>0.01){
  vec3 cl=texture2D(uTexSampler,uv-vec2(uTexel.x,0.0)).rgb;
  vec3 cr=texture2D(uTexSampler,uv+vec2(uTexel.x,0.0)).rgb;
  vec3 ct=texture2D(uTexSampler,uv-vec2(0.0,uTexel.y)).rgb;
  vec3 cb=texture2D(uTexSampler,uv+vec2(0.0,uTexel.y)).rgb;
  float yc=luma709(e);
  float yl=luma709(cl),yr=luma709(cr),yt=luma709(ct),yb=luma709(cb);
  float lumaEdge=max(abs(yr-yl),abs(yb-yt));
  vec2 cc=chromaRG(e),cL=chromaRG(cl),cR=chromaRG(cr),cT=chromaRG(ct),cB=chromaRG(cb);
  float chromaEdge=max(length(cR-cL),length(cB-cT));
  float smoothLuma=1.0-smoothstep(0.012,0.040,lumaEdge);
  float safeChroma=1.0-smoothstep(0.025,0.075,chromaEdge);
  vec2 avgC=(cc*2.0+cL+cR+cT+cB)/6.0;
  float reconstruct=smoothLuma*safeChroma*mix(0.04,0.24,uDeband)*uChromaScale;
  vec2 repairedC=mix(cc,avgC,reconstruct);
  e=clamp(fromLumaChroma(yc,repairedC),0.0,1.0);
 }

 // P4/P5 Anime + Animation line/reconstruction engine.
 if(uAnimeEnabled>0.5){
  vec3 aL=texture2D(uTexSampler,uv-vec2(uTexel.x,0.0)).rgb;
  vec3 aR=texture2D(uTexSampler,uv+vec2(uTexel.x,0.0)).rgb;
  vec3 aT=texture2D(uTexSampler,uv-vec2(0.0,uTexel.y)).rgb;
  vec3 aB=texture2D(uTexSampler,uv+vec2(0.0,uTexel.y)).rgb;
  vec3 aTL=texture2D(uTexSampler,uv-vec2(uTexel.x,uTexel.y)).rgb;
  vec3 aTR=texture2D(uTexSampler,uv+vec2(uTexel.x,-uTexel.y)).rgb;
  vec3 aBL=texture2D(uTexSampler,uv+vec2(-uTexel.x,uTexel.y)).rgb;
  vec3 aBR=texture2D(uTexSampler,uv+vec2(uTexel.x,uTexel.y)).rgb;

  float ac=luma709(e);
  float al=luma709(aL),ar=luma709(aR),at=luma709(aT),ab=luma709(aB);
  float atl=luma709(aTL),atr=luma709(aTR),abl=luma709(aBL),abr=luma709(aBR);
  float axisEdge=max(abs(ar-al),abs(ab-at));
  float diagEdge=max(abs(abr-atl),abs(abl-atr));
  float edge=max(axisEdge,diagEdge);

  float localMin=min(min(min(al,ar),min(at,ab)),min(min(atl,atr),min(abl,abr)));
  float localMax=max(max(max(al,ar),max(at,ab)),max(max(atl,atr),max(abl,abr)));
  float span=localMax-localMin;

  float lineMask=smoothstep(0.035,0.12,edge);
  float flatMask=1.0-smoothstep(0.018,0.060,span);
  float hardEdgeGuard=1.0-smoothstep(uAnimeHalo,1.0,span);

  float neighbourY=(al+ar+at+ab)*0.20+(atl+atr+abl+abr)*0.05;
  float lineDelta=ac-neighbourY;
  float safeLine=lineMask*(1.0-flatMask*uAnimeFlat)*hardEdgeGuard;
  float targetY=clamp(ac+lineDelta*uAnimeLine*safeLine,localMin,localMax);
  e=clamp(fromLumaChroma(targetY,chromaRG(e)),0.0,1.0);

  float hDiff=abs(al-ar),vDiff=abs(at-ab);
  float d1Diff=abs(atl-abr),d2Diff=abs(atr-abl);
  float axisMin=min(hDiff,vDiff),diagMin=min(d1Diff,d2Diff);
  float axisWeight=1.0/(0.002+axisMin);
  float diagWeight=uAnimeDiagonal/(0.002+diagMin);
  float norm=axisWeight+diagWeight;
  float axisY=mix((al+ar)*0.5,(at+ab)*0.5,step(vDiff,hDiff));
  float diagY=mix((atl+abr)*0.5,(atr+abl)*0.5,step(d2Diff,d1Diff));
  float reconstructedY=(axisY*axisWeight+diagY*diagWeight)/max(norm,0.0001);

  vec2 cL=chromaRG(aL),cR=chromaRG(aR),cT=chromaRG(aT),cB=chromaRG(aB);
  float colourBoundary=max(length(cR-cL),length(cB-cT));
  float colourSafe=1.0-smoothstep(0.020,uAnimeChromaGuard*0.10,colourBoundary);
  float reconstructionMask=lineMask*(1.0-flatMask*uAnimeFlat)*hardEdgeGuard*colourSafe;
  float rebuiltY=mix(luma709(e),reconstructedY,uAnimeReconstruct*reconstructionMask);
  rebuiltY=clamp(rebuiltY,localMin,localMax);
  e=clamp(fromLumaChroma(rebuiltY,chromaRG(e)),0.0,1.0);
 }

 // P6-S2 Movie Engine. Conservative live-action detail recovery in the same GPU pass.
 // Texture/grain, skin, chroma boundaries and already-hard edges suppress recovery.
 if(uMovieEnabled>0.5 && uMovieDetail>0.001){
  vec3 mL=texture2D(uTexSampler,uv-vec2(uTexel.x,0.0)).rgb;
  vec3 mR=texture2D(uTexSampler,uv+vec2(uTexel.x,0.0)).rgb;
  vec3 mT=texture2D(uTexSampler,uv-vec2(0.0,uTexel.y)).rgb;
  vec3 mB=texture2D(uTexSampler,uv+vec2(0.0,uTexel.y)).rgb;
  float mc=luma709(e),ml=luma709(mL),mr=luma709(mR),mt=luma709(mT),mb=luma709(mB);
  float localMin=min(mc,min(min(ml,mr),min(mt,mb)));
  float localMax=max(mc,max(max(ml,mr),max(mt,mb)));
  float span=localMax-localMin;
  float lap=abs(mc-(ml+mr+mt+mb)*0.25);

  vec2 cc=chromaRG(e);
  float chromaEdge=max(length(chromaRG(mR)-chromaRG(mL)),
                       length(chromaRG(mB)-chromaRG(mT)));
  float textureSafe=1.0-smoothstep(0.010,0.050*uMovieTexture,lap);
  float grainSafe=1.0-smoothstep(0.014,0.060*uMovieGrainProtect,span);
  float haloSafe=1.0-smoothstep(0.045,0.16*uMovieHalo,span);
  float chromaSafe=1.0-smoothstep(0.018,0.090*uMovieChromaGuard,chromaEdge);
  float faceSafe=1.0-skinMask(e)*uMovieSkinProtect;
  float recoveryMask=textureSafe*grainSafe*haloSafe*chromaSafe*faceSafe;

  float neighbourY=(ml+mr+mt+mb)*0.25;
  float recoveredY=clamp(mc+(mc-neighbourY)*uMovieDetail*recoveryMask,localMin,localMax);
  e=clamp(fromLumaChroma(recoveredY,cc),0.0,1.0);
 }

 // Existing CAS-style adaptive sharpen, with a P6 film ceiling.
 vec3 sb=texture2D(uTexSampler,uv+vec2(0.0,-uTexel.y)).rgb;
 vec3 sd=texture2D(uTexSampler,uv+vec2(-uTexel.x,0.0)).rgb;
 vec3 sf=texture2D(uTexSampler,uv+vec2(uTexel.x,0.0)).rgb;
 vec3 sh=texture2D(uTexSampler,uv+vec2(0.0,uTexel.y)).rgb;
 vec3 mn=min(min(min(sd,e),min(sf,sb)),sh),mx=max(max(max(sd,e),max(sf,sb)),sh);
 vec3 amp=sqrt(clamp(min(mn,1.0-mx)/max(mx,vec3(0.0001)),0.0,1.0));
 float localSpan=max(max(mx.r-mn.r,mx.g-mn.g),mx.b-mn.b);
 float edgeGuard=1.0-smoothstep(uSharpenGuard,1.0,localSpan);
 float effectiveSharpen=uSharpen;
 if(uMovieEnabled>0.5) effectiveSharpen=min(effectiveSharpen,uMovieSharpenCeiling);
 float peak=-1.0/mix(9.0,4.0,effectiveSharpen*edgeGuard); vec3 w=amp*peak;
 vec3 col=clamp((sb*w+sd*w+sf*w+sh*w+e)/(1.0+4.0*w),0.0,1.0);

 float lum=luma709(col),mxc=max(col.r,max(col.g,col.b)),mnc=min(col.r,min(col.g,col.b));
 float chroma=mxc-mnc,skin=skinMask(col);
 float vib=uColour*(1.0-chroma)*(1.0-0.75*skin);
 col=clamp(mix(vec3(lum),col,1.0+1.6*vib),0.0,1.0);

 float lo=luma709(col); vec2 ns=gl_FragCoord.xy+vec2(uSeed*113.0,uSeed*71.0);
 float n=rand(ns)+rand(ns*1.37+17.0)-1.0;
 float mask=smoothstep(0.03,0.12,lo)*(1.0-0.5*smoothstep(0.85,1.0,lo));
 col+=n*(uDeband*(0.85/255.0)+uGrain*0.035)*mask;

 vec3 outc=mix(e0,clamp(col,0.0,1.0),uAmount);
 if(uSplit>0.0){float sx=vTexSamplingCoord.x;if(sx>uSplit)outc=e0;
  if(abs(sx-uSplit)<uTexel.x*1.5)outc=vec3(1.0,0.75,0.2);}
 gl_FragColor=vec4(outc,1.0);
}"""
    }
}
