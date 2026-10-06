package com.sole.cinevault.picture

/** Pure JVM-testable safety policy mirroring P3-S1 repair thresholds. */
object PictureRepairPolicy {
    data class Limits(
        val radiusPx: ClosedFloatingPointRange<Float>,
        val maxBlend: Float,
        val ditherLsb: Float,
    )

    fun limits(deband: Float): Limits {
        val d=deband.coerceIn(0f,1f)
        val base=1.5f+3.2f*d
        return Limits((base-0.4f)..(base+0.4f),0.18f+0.44f*d,0.85f*d)
    }

    fun eligible(deband:Float,lumaRange:Float,chromaRange:Float,laplacian:Float):Boolean{
        val d=deband.coerceIn(0f,1f)
        if(d<=0.01f)return false
        return lumaRange<lerp(0.006f,0.020f,d) &&
            chromaRange<lerp(0.008f,0.026f,d) &&
            laplacian<lerp(0.0025f,0.0090f,d)
    }

    private fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t
}
