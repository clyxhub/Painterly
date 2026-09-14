package com.painterly.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StageParametersTest {

    @Test
    fun identicalParametersAffectNoLayers() {
        val a = StageParameters()
        val b = StageParameters()
        assertEquals(emptySet<LayerId>(), b.changedLayers(a))
    }

    @Test
    fun drawingChangeOnlyAffectsDrawingLayer() {
        val a = StageParameters()
        val b = a.copy(drawingDetail = 0.9f)
        assertEquals(setOf(LayerId.DRAWING), b.changedLayers(a))
    }

    @Test
    fun accentCountOnlyAffectsAccentsLayer() {
        val a = StageParameters()
        val b = a.copy(accentCount = 30)
        assertEquals(setOf(LayerId.ACCENTS), b.changedLayers(a))
    }

    @Test
    fun refinementChangeOnlyAffectsItsOverlay() {
        val a = StageParameters()
        val b = a.copy(refinement = a.refinement.copy(texture = 0.1f))
        assertEquals(setOf(LayerId.REFINE_TEXTURE), b.changedLayers(a))
    }

    @Test
    fun cacheKeysDifferWhenParametersDiffer() {
        val a = StageParameters()
        val b = a.copy(shadowAmount = 0.8f)
        assertEquals(true, a.keyFor(LayerId.SHADOW_MASS) != b.keyFor(LayerId.SHADOW_MASS))
    }
}
