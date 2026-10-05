package com.sole.cinevault.picture

enum class PictureProcessingStage {
    SOURCE_ANALYSIS, ARTIFACT_REPAIR, CHROMA_RECONSTRUCTION, RECONSTRUCTION,
    SCALE, ADAPTIVE_SHARPEN, COLOUR_TONE, TEXTURE, DISPLAY_ADAPTATION,
}
enum class PictureQualityTier { ECO, BALANCED, MAX }
enum class PictureStageCapability { AVAILABLE, PLANNED, UNSUPPORTED }

data class PictureStageSpec(
    val stage: PictureProcessingStage,
    val capability: PictureStageCapability,
    val enabled: Boolean,
    val bypassable: Boolean = true,
)

data class PicturePipelineProfile(
    val id: String,
    val content: PictureContent,
    val qualityTier: PictureQualityTier,
    val stages: List<PictureStageSpec>,
) {
    init {
        require(id.isNotBlank()) { "Picture pipeline id must not be blank" }
        require(stages.map { it.stage }.distinct().size == stages.size) {
            "A picture processing stage may appear only once in a pipeline"
        }
    }
    fun stage(stage: PictureProcessingStage) = stages.firstOrNull { it.stage == stage }
    fun isRuntimeReady() =
        stages.filter { it.enabled }.all { it.capability == PictureStageCapability.AVAILABLE }
}

/** Describes today's V1 in the future pipeline vocabulary; does not alter runtime output. */
object PicturePipelineProfiles {
    fun v1Foundation(
        content: PictureContent,
        qualityTier: PictureQualityTier = PictureQualityTier.ECO,
    ) = PicturePipelineProfile(
        id = "v1-foundation-${content.name.lowercase()}",
        content = content,
        qualityTier = qualityTier,
        stages = listOf(
            PictureStageSpec(PictureProcessingStage.SOURCE_ANALYSIS, PictureStageCapability.AVAILABLE, true),
            PictureStageSpec(PictureProcessingStage.ARTIFACT_REPAIR, PictureStageCapability.AVAILABLE, true),
            PictureStageSpec(PictureProcessingStage.CHROMA_RECONSTRUCTION, PictureStageCapability.PLANNED, false),
            PictureStageSpec(PictureProcessingStage.RECONSTRUCTION, PictureStageCapability.PLANNED, false),
            PictureStageSpec(PictureProcessingStage.SCALE, PictureStageCapability.PLANNED, false),
            PictureStageSpec(PictureProcessingStage.ADAPTIVE_SHARPEN, PictureStageCapability.AVAILABLE, true),
            PictureStageSpec(PictureProcessingStage.COLOUR_TONE, PictureStageCapability.AVAILABLE, true),
            PictureStageSpec(
                PictureProcessingStage.TEXTURE,
                PictureStageCapability.AVAILABLE,
                content == PictureContent.FILM || content == PictureContent.AUTO,
            ),
            PictureStageSpec(PictureProcessingStage.DISPLAY_ADAPTATION, PictureStageCapability.PLANNED, false),
        ),
    )
}
