package com.painterly.app.model

/** A locally stored painting project. Its files live in files/projects/<id>/. */
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val referenceWidth: Int,
    val referenceHeight: Int,
    val settings: StageSettings = StageSettings(),
    val lastStage: PaintingStage = PaintingStage.DRAW,
) {
    val aspectRatio: Float
        get() = if (referenceHeight <= 0) 1f else referenceWidth.toFloat() / referenceHeight.toFloat()
}

/** Optional drawing grid drawn in image space so it scales and pans with the artwork. */
data class GridSettings(
    val enabled: Boolean = false,
    val rows: Int = 3,
    val columns: Int = 4,
    val opacity: Float = 0.45f,
    val labels: Boolean = false,
)
