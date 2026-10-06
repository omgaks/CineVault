package com.sole.cinevault.picture

enum class PictureContent(val label: String) { AUTO("Auto"), ANIME("Anime"), ANIMATION("Animation"), FILM("Film") }
enum class PicturePreset(val label: String) { OFF("Off"), NATURAL("Natural"), CINEMA("Cinema"), VIVID("Vivid"), SHARP("Sharp"), CUSTOM("Custom") }

data class PictureSettings(
    val preset: PicturePreset = PicturePreset.OFF,
    val content: PictureContent = PictureContent.AUTO,
    val intensity: Float = 0.9f,
    val sharpen: Float = 0.5f,
    val deband: Float = 0.5f,
    val colour: Float = 0.15f,
    val grain: Float = 0f,
)

data class PictureShaderParams(
    val amount: Float,
    val sharpen: Float,
    val deband: Float,
    val colour: Float,
    val grain: Float,
    val split: Float = 0f,
    /** Runtime-only resolved content. Not persisted. */
    val content: PictureContent = PictureContent.FILM,
) {
    companion object { val OFF=PictureShaderParams(0f,0f,0f,0f,0f,0f,PictureContent.FILM) }
}

object PictureProfiles {
    data class Tune(val sharpen:Float,val deband:Float,val colour:Float,val grain:Float)
    fun tune(preset:PicturePreset,content:PictureContent):Tune {
        val base=when(preset){
            PicturePreset.NATURAL->Tune(.50f,.50f,.15f,0f)
            PicturePreset.CINEMA->Tune(.40f,.60f,.05f,.15f)
            PicturePreset.VIVID->Tune(.50f,.50f,.45f,0f)
            PicturePreset.SHARP->Tune(.90f,.40f,.15f,0f)
            PicturePreset.OFF,PicturePreset.CUSTOM->Tune(.50f,.50f,.15f,0f)
        }
        return when(content){
            PictureContent.ANIME->Tune(base.sharpen*.85f,(base.deband+.15f).coerceAtMost(1f),(base.colour*1.2f).coerceAtMost(1f),0f)
            PictureContent.ANIMATION->Tune(base.sharpen*.80f,(base.deband+.10f).coerceAtMost(1f),base.colour,0f)
            PictureContent.FILM,PictureContent.AUTO->base
        }
    }
    fun resolveContent(selected:PictureContent,detected:PictureContent)=if(selected==PictureContent.AUTO)detected else selected
    fun withPreset(settings:PictureSettings,preset:PicturePreset,resolvedContent:PictureContent):PictureSettings {
        val x=tune(preset,resolvedContent)
        return settings.copy(preset=preset,sharpen=x.sharpen,deband=x.deband,colour=x.colour,grain=x.grain)
    }
    fun toShaderParams(settings:PictureSettings,comparing:Boolean,active:Boolean,splitView:Boolean=false,splitPosition:Float=.5f):PictureShaderParams {
        if(!active||settings.preset==PicturePreset.OFF)return PictureShaderParams.OFF
        return PictureShaderParams(
            amount=if(comparing)0f else settings.intensity.coerceIn(0f,1f),
            sharpen=settings.sharpen.coerceIn(0f,1f),deband=settings.deband.coerceIn(0f,1f),
            colour=settings.colour.coerceIn(0f,1f),grain=settings.grain.coerceIn(0f,1f),
            split=if(splitView&&!comparing)splitPosition.coerceIn(.05f,.95f) else 0f,
            content=settings.content,
        )
    }
    fun resetFineTune(settings:PictureSettings,resolvedContent:PictureContent,fallback:PicturePreset):PictureSettings {
        val preset=when(settings.preset){PicturePreset.OFF->PicturePreset.OFF;PicturePreset.CUSTOM->fallback;else->settings.preset}
        val x=tune(if(preset==PicturePreset.OFF)fallback else preset,resolvedContent)
        return settings.copy(preset=preset,sharpen=x.sharpen,deband=x.deband,colour=x.colour,grain=x.grain)
    }
    fun resetAll(settings:PictureSettings,detected:PictureContent):PictureSettings {
        val fresh=PictureSettings(preset=if(settings.preset!=PicturePreset.OFF)PicturePreset.NATURAL else PicturePreset.OFF,content=PictureContent.AUTO)
        return resetFineTune(fresh,resolveContent(PictureContent.AUTO,detected),PicturePreset.NATURAL)
    }
}
object PictureSettingsCodec {
    fun encode(s:PictureSettings)=listOf(s.preset.name,s.content.name,s.intensity,s.sharpen,s.deband,s.colour,s.grain).joinToString(",")
    fun decode(text:String?):PictureSettings? {
        if(text.isNullOrBlank())return null
        val p=text.split(",");if(p.size!=7)return null
        return try{PictureSettings(PicturePreset.valueOf(p[0]),PictureContent.valueOf(p[1]),p[2].toFloat().coerceIn(0f,1f),
            p[3].toFloat().coerceIn(0f,1f),p[4].toFloat().coerceIn(0f,1f),p[5].toFloat().coerceIn(0f,1f),p[6].toFloat().coerceIn(0f,1f))}
        catch(_:IllegalArgumentException){null}
    }
}
