package com.raweditor.app

/**
 * A colour preset = a set of photographic adjustments applied in a fixed order.
 * All values are neutral at their defaults; a preset describes a natural,
 * detail-preserving look rather than a stylised filter.
 */
data class ColorPreset(
    val id: String,
    val displayName: String,
    val description: String,
    // Exposure / tone
    val exposure: Float = 1.0f,        // linear gain, 1.0 = unchanged
    val contrast: Float = 1.0f,        // S-curve strength, 1.0 = unchanged
    val highlights: Float = 0f,        // -1..1, negative recovers highlights
    val shadows: Float = 0f,           // -1..1, positive lifts shadows
    // Colour
    val temperature: Float = 0f,       // -1 (cool) .. 1 (warm)
    val tint: Float = 0f,              // -1 (green) .. 1 (magenta)
    val saturation: Float = 1.0f,      // 1.0 = unchanged
    val vibrance: Float = 0f,          // -1..1, protects skin tones
    // Detail
    val clarity: Float = 0f,           // -1..1 local contrast
    val sharpness: Float = 0f          // -1..1
)

object Presets {

    /**
     * Four natural looks, ordered light -> punchy.
     * Every preset preserves highlight/shadow detail and stays colour-accurate.
     */
    val ALL: List<ColorPreset> = listOf(
        ColorPreset(
            id = "natural",
            displayName = "Natural 自然",
            description = "色彩真实，轻微提亮对比，最大限度保留细节",
            exposure = 1.02f,
            contrast = 1.06f,
            highlights = -0.10f,
            shadows = 0.10f,
            saturation = 1.03f,
            vibrance = 0.05f
        ),
        ColorPreset(
            id = "natural_contrast",
            displayName = "Natural+ 立体",
            description = "在原色基础上加强明暗对比，画面更立体通透",
            exposure = 1.02f,
            contrast = 1.18f,
            highlights = -0.18f,
            shadows = 0.16f,
            saturation = 1.05f,
            vibrance = 0.08f,
            clarity = 0.10f
        ),
        ColorPreset(
            id = "film_soft",
            displayName = "Film Soft 柔和",
            description = "胶片般柔和过渡，高光微收，层次丰富自然",
            exposure = 1.03f,
            contrast = 1.02f,
            highlights = -0.22f,
            shadows = 0.22f,
            temperature = 0.05f,
            saturation = 0.96f,
            vibrance = 0.04f
        ),
        ColorPreset(
            id = "clarity",
            displayName = "Clarity 细节",
            description = "强化局部细节与质感，保持自然不锐化过头",
            exposure = 1.0f,
            contrast = 1.10f,
            highlights = -0.14f,
            shadows = 0.14f,
            saturation = 1.04f,
            clarity = 0.30f,
            sharpness = 0.15f
        )
    )

    fun byId(id: String): ColorPreset =
        ALL.firstOrNull { it.id == id } ?: ALL.first()
}
