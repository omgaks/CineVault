package com.sole.cinevault.picture

/** Pure policy for the conservative P3-S2 chroma reconstruction stage. */
object PictureChromaPolicy {
    data class Decision(val enabled:Boolean,val strength:Float)

    fun decide(deband:Float,lumaEdge:Float,chromaEdge:Float):Decision {
        val d=deband.coerceIn(0f,1f)
        if(d<=0.01f)return Decision(false,0f)
        val smooth=1f-smoothstep(0.012f,0.040f,lumaEdge.coerceAtLeast(0f))
        val safe=1f-smoothstep(0.025f,0.075f,chromaEdge.coerceAtLeast(0f))
        val strength=smooth*safe*lerp(0.04f,0.24f,d)
        return Decision(strength>0.001f,strength.coerceIn(0f,0.24f))
    }

    private fun smoothstep(a:Float,b:Float,x:Float):Float {
        val t=((x-a)/(b-a)).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    private fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t
}
