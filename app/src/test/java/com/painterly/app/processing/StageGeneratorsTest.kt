package com.painterly.app.processing

import com.painterly.app.model.Argb
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class StageGeneratorsTest {

    private fun gradient(w: Int, h: Int): IntArray = IntArray(w * h) { index ->
        val x = index % w
        val y = index / w
        Argb.pack(255, x * 255 / w, y * 255 / h, 128)
    }

    @Test
    fun everyLayerReturnsCorrectlySizedOutput() {
        val w = 64
        val h = 48
        val source = gradient(w, h)
        val params = StageParameters()
        LayerId.entries.forEach { layer ->
            val output = StageGenerators.generate(layer, source, w, h, params)
            assertEquals("layer $layer", w * h, output.size)
        }
    }

    @Test
    fun blockInReturnsOpaquePixels() {
        val w = 32
        val h = 32
        val source = gradient(w, h)
        val output = StageGenerators.generate(LayerId.BLOCK_IN, source, w, h, StageParameters())
        output.forEach { assertEquals(255, Argb.alpha(it)) }
    }

    @Test
    fun drawingContainsOnlyOpaqueOrTransparentPixels() {
        val w = 48
        val h = 48
        val source = gradient(w, h)
        val output = StageGenerators.generate(LayerId.DRAWING, source, w, h, StageParameters())
        output.forEach { pixel ->
            val alpha = Argb.alpha(pixel)
            assertEquals(true, alpha == 0 || alpha == 255)
        }
    }
}
