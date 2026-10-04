package com.sole.cinevault.picture

/**
 * Picture enhancement — pure data and tuning tables (no Android/Media3 types, so the
 * logic here is covered by plain JVM tests).
 */
enum class PictureContent(val label: String) {
    AUTO("Auto"),
    ANIME("Anime"),
    ANIMATION("Animation"),
    FILM("Film"),
}

enum class PicturePreset(val label: String) {
    OFF("Off"),
    NATURAL("Natural"),
    CINEMA("Cinema"),
    VIVID("Vivid"),
    SHARP("Sharp"),
    CUSTOM("Custom"),
}

/** Everything the user can change. Slider values are 0..1. */
data class PictureSettings(
    val preset: PicturePreset = PicturePreset.OFF,
    val content: PictureContent = PictureContent.AUTO,
    val intensity: Float = 0.9f,
    val sharpen: Float = 0.5f,
    val deband: Float = 0.5f,
    val colour: Float = 0.15f,
    val grain: Float = 0f,
)

/** What the GPU shader reads every frame. */
data class PictureShaderParams(
    val amount: Float,
    val sharpen: Float,
    val deband: Float,
    val colour: Float,
    val grain: Float,
    /** 0 = off; otherwise the x position (0..1) of the split-view divider. */
    val split: Float = 0f,
) {
    companion object {
        val OFF = PictureShaderParams(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

object PictureProfiles {

    data class Tune(
        val sharpen: Float,
        val deband: Float,
        val colour: Float,
        val grain: Float,
    )

    fun tune(preset: PicturePreset, content: PictureContent): Tune {
        // Values chosen so each look is clearly visible on a real 1080p frame.
        val base = when (preset) {
            PicturePreset.NATURAL -> Tune(0.50f, 0.50f, 0.15f, 0.00f)
            PicturePreset.CINEMA -> Tune(0.40f, 0.60f, 0.05f, 0.15f)
            PicturePreset.VIVID -> Tune(0.50f, 0.50f, 0.45f, 0.00f)
            PicturePreset.SHARP -> Tune(0.90f, 0.40f, 0.15f, 0.00f)
            PicturePreset.OFF, PicturePreset.CUSTOM -> Tune(0.50f, 0.50f, 0.15f, 0.00f)
        }
        return when (content) {
            // 2D anime: flat colour fills band easily, line art needs a lighter hand,
            // and film grain would look wrong.
            PictureContent.ANIME -> Tune(
                sharpen = base.sharpen * 0.85f,
                deband = (base.deband + 0.15f).coerceAtMost(1f),
                colour = (base.colour * 1.2f).coerceAtMost(1f),
                grain = 0f,
            )
            // 3D / western animation: smooth shading, no grain.
            PictureContent.ANIMATION -> Tune(
                sharpen = base.sharpen * 0.80f,
                deband = (base.deband + 0.10f).coerceAtMost(1f),
                colour = base.colour,
                grain = 0f,
            )
            PictureContent.FILM, PictureContent.AUTO -> base
        }
    }

    fun resolveContent(selected: PictureContent, detected: PictureContent): PictureContent =
        if (selected == PictureContent.AUTO) detected else selected

    /** Applies a preset's tuning for the given (already resolved) content type. */
    fun withPreset(
        settings: PictureSettings,
        preset: PicturePreset,
        resolvedContent: PictureContent,
    ): PictureSettings {
        val t = tune(preset, resolvedContent)
        return settings.copy(
            preset = preset,
            sharpen = t.sharpen,
            deband = t.deband,
            colour = t.colour,
            grain = t.grain,
        )
    }

    fun toShaderParams(
        settings: PictureSettings,
        comparing: Boolean,
        active: Boolean,
        splitView: Boolean = false,
        splitPosition: Float = 0.5f,
    ): PictureShaderParams {
        if (!active || settings.preset == PicturePreset.OFF) return PictureShaderParams.OFF
        val amount = if (comparing) 0f else settings.intensity.coerceIn(0f, 1f)
        return PictureShaderParams(
            amount = amount,
            sharpen = settings.sharpen.coerceIn(0f, 1f),
            deband = settings.deband.coerceIn(0f, 1f),
            colour = settings.colour.coerceIn(0f, 1f),
            grain = settings.grain.coerceIn(0f, 1f),
            split = if (splitView && !comparing) splitPosition.coerceIn(0.05f, 0.95f) else 0f,
        )
    }

    /** Fine-tune sliders back to the current look's own values (Custom falls back to [fallback]). */
    fun resetFineTune(
        settings: PictureSettings,
        resolvedContent: PictureContent,
        fallback: PicturePreset,
    ): PictureSettings {
        val preset = when (settings.preset) {
            PicturePreset.OFF -> PicturePreset.OFF
            PicturePreset.CUSTOM -> fallback
            else -> settings.preset
        }
        val t = tune(if (preset == PicturePreset.OFF) fallback else preset, resolvedContent)
        return settings.copy(
            preset = preset,
            sharpen = t.sharpen,
            deband = t.deband,
            colour = t.colour,
            grain = t.grain,
        )
    }

    /** Everything back to defaults, keeping Picture on or off as it was. */
    fun resetAll(settings: PictureSettings, detected: PictureContent): PictureSettings {
        val on = settings.preset != PicturePreset.OFF
        val fresh = PictureSettings(
            preset = if (on) PicturePreset.NATURAL else PicturePreset.OFF,
            content = PictureContent.AUTO,
        )
        return resetFineTune(fresh, resolveContent(PictureContent.AUTO, detected), PicturePreset.NATURAL)
    }
}

/** Compact text form used for per-title memory. */
object PictureSettingsCodec {

    fun encode(s: PictureSettings): String =
        listOf(
            s.preset.name,
            s.content.name,
            s.intensity.toString(),
            s.sharpen.toString(),
            s.deband.toString(),
            s.colour.toString(),
            s.grain.toString(),
        ).joinToString(",")

    fun decode(text: String?): PictureSettings? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(",")
        if (parts.size != 7) return null
        return try {
            PictureSettings(
                preset = PicturePreset.valueOf(parts[0]),
                content = PictureContent.valueOf(parts[1]),
                intensity = parts[2].toFloat().coerceIn(0f, 1f),
                sharpen = parts[3].toFloat().coerceIn(0f, 1f),
                deband = parts[4].toFloat().coerceIn(0f, 1f),
                colour = parts[5].toFloat().coerceIn(0f, 1f),
                grain = parts[6].toFloat().coerceIn(0f, 1f),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
