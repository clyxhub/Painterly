package com.painterly.app.model

/** The two ways of reading a stage. Never hidden in a menu. */
enum class ViewMode(val label: String) {
    ADD("Add"),
    COMPLETE("Complete"),
}

/** Which refinement view Stage 7 shows. */
enum class RefineView(val label: String) {
    EDGES("Edges"),
    DETAIL("Detail"),
}

/**
 * All painter-facing controls. Values are normalized (0..1) so the UI never exposes
 * any computer-vision parameter; the pipeline maps them to real algorithm settings.
 */
data class StageSettings(
    val drawDetail: Float = 0.5f,
    val blockInValues: Int = 4,
    val shadowAmount: Float = 0.5f,
    val lightAmount: Float = 0.5f,
    val formAmount: Float = 0.5f,
    val accentAmount: Float = 0.5f,
    val refineView: RefineView = RefineView.EDGES,
    val refineAmount: Float = 0.5f,
) {
    /**
     * A compact signature for each stage describing exactly which inputs it depends on.
     * Used for cache keys and to recompute only the stages affected by a change.
     */
    fun dependencyKey(stage: PaintingStage): String = when (stage) {
        PaintingStage.DRAW -> "d=%.3f".format(drawDetail)
        PaintingStage.BLOCK_IN -> "b=$blockInValues"
        PaintingStage.SHADOWS -> "b=$blockInValues;s=%.3f".format(shadowAmount)
        PaintingStage.LIGHTS -> "b=$blockInValues;l=%.3f".format(lightAmount)
        PaintingStage.FORM -> "b=$blockInValues;f=%.3f".format(formAmount)
        PaintingStage.ACCENTS -> "b=$blockInValues;a=%.3f".format(accentAmount)
        PaintingStage.REFINE ->
            "b=$blockInValues;v=${refineView.name};r=%.3f".format(refineAmount)
    }

    fun fullSignature(): String = PaintingStage.entries.joinToString("|") { dependencyKey(it) }
}
