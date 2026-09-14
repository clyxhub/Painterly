package com.painterly.app.model

/**
 * All user-facing adjustment values. Every field is a simple 0..1 "direction"
 * slider (or a tiny count/choice) so the UI can speak in artistic language
 * rather than exposing computer-vision internals.
 */
data class StageParameters(
    val drawingDetail: Float = 0.55f,
    val drawingSensitivity: Float = 0.5f,
    val drawingSimplification: Float = 0.45f,
    val blockInValues: Int = 4,
    val blockInSimplification: Float = 0.5f,
    val shadowAmount: Float = 0.5f,
    val lightAmount: Float = 0.5f,
    val coreAmount: Float = 0.5f,
    val accentStrength: Float = 0.55f,
    val accentCount: Int = 14,
    val refinement: RefinementParameters = RefinementParameters(),
) {
    fun keyFor(layer: LayerId): String = when (layer) {
        LayerId.DRAWING ->
            "detail=$drawingDetail;sens=$drawingSensitivity;simpl=$drawingSimplification"
        LayerId.BLOCK_IN ->
            "values=$blockInValues;simpl=$blockInSimplification"
        LayerId.SHADOW_MASS -> "amount=$shadowAmount"
        LayerId.LIGHT_MASS -> "amount=$lightAmount"
        LayerId.CORE_SHADOWS -> "amount=$coreAmount"
        LayerId.ACCENTS -> "strength=$accentStrength;count=$accentCount"
        LayerId.REFINE_EDGE -> "edge=${refinement.edgeControl}"
        LayerId.REFINE_DETAIL -> "detail=${refinement.detailPriority}"
        LayerId.REFINE_TEXTURE -> "texture=${refinement.texture}"
        LayerId.REFINE_COLOR -> "colour=${refinement.colourVariation}"
    }

    /** Layers whose appearance depends on a field that changed since [old]. */
    fun changedLayers(old: StageParameters): Set<LayerId> = buildSet {
        if (drawingDetail != old.drawingDetail ||
            drawingSensitivity != old.drawingSensitivity ||
            drawingSimplification != old.drawingSimplification
        ) add(LayerId.DRAWING)
        if (blockInValues != old.blockInValues ||
            blockInSimplification != old.blockInSimplification
        ) add(LayerId.BLOCK_IN)
        if (shadowAmount != old.shadowAmount) add(LayerId.SHADOW_MASS)
        if (lightAmount != old.lightAmount) add(LayerId.LIGHT_MASS)
        if (coreAmount != old.coreAmount) add(LayerId.CORE_SHADOWS)
        if (accentStrength != old.accentStrength || accentCount != old.accentCount) add(LayerId.ACCENTS)
        if (refinement.edgeControl != old.refinement.edgeControl) add(LayerId.REFINE_EDGE)
        if (refinement.detailPriority != old.refinement.detailPriority) add(LayerId.REFINE_DETAIL)
        if (refinement.texture != old.refinement.texture) add(LayerId.REFINE_TEXTURE)
        if (refinement.colourVariation != old.refinement.colourVariation) add(LayerId.REFINE_COLOR)
    }
}

data class RefinementParameters(
    val edgeControl: Float = 0.5f,
    val detailPriority: Float = 0.5f,
    val texture: Float = 0.5f,
    val colourVariation: Float = 0.5f,
)
