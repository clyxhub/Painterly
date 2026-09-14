package com.painterly.app.model

/**
 * The seven progressive painting stages, in the order a painter works through them.
 * The [question] is the painter-facing prompt shown in the UI.
 */
enum class PaintingStage(val label: String, val question: String) {
    DRAW("Draw", "What do I need to draw before painting?"),
    BLOCK_IN("Block-In", "What are the largest masses I put down first?"),
    SHADOWS("Shadows", "What dark information do I add next?"),
    LIGHTS("Lights", "What major light shapes do I add next?"),
    FORM("Form", "Where do the major forms begin to turn?"),
    ACCENTS("Accents", "What small information brings the painting forward?"),
    REFINE("Refine", "Where should I spend my time refining?");

    val index: Int get() = ordinal

    companion object {
        fun fromIndex(index: Int): PaintingStage = entries[index.coerceIn(0, entries.lastIndex)]
    }
}
