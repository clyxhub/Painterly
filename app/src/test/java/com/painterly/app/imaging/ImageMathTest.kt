package com.painterly.app.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the deterministic image math (no Android framework involved). */
class ImageMathTest {

    @Test
    fun boxBlurKeepsFlatImageFlat() {
        val width = 8
        val height = 8
        val source = FloatArray(width * height) { 100f }
        val blurred = ImageMath.boxBlur(source, width, height, 2)
        for (value in blurred) assertEquals(100.0, value.toDouble(), 0.01)
    }

    @Test
    fun gaussianBlurReturnsSameSize() {
        val width = 16
        val height = 10
        val source = FloatArray(width * height) { it % 255f }
        val blurred = ImageMath.gaussianBlur(source, width, height, 2f)
        assertTrue(blurred.size == source.size)
    }

    @Test
    fun cannyFindsAStepEdge() {
        val width = 40
        val height = 20
        val gray = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                gray[y * width + x] = if (x < 20) 0f else 200f
            }
        }
        val edges = ImageMath.cannyEdges(gray, width, height, low = 20f, high = 40f, minComponent = 3)
        var nearBoundary = 0
        for (y in 0 until height) {
            for (x in 18..21) if (edges[y * width + x]) nearBoundary++
        }
        assertTrue("expected edge pixels near the step", nearBoundary > 0)
    }

    @Test
    fun percentileValueLandsInExpectedBand() {
        val values = FloatArray(100) { it.toFloat() }
        val median = ImageMath.percentileValue(values, 0.5f)
        val top = ImageMath.percentileValue(values, 1f)
        assertTrue(median in 45f..55f)
        assertTrue(top >= 99f)
    }

    @Test
    fun modeFilterRemovesIsolatedSpeckle() {
        val width = 5
        val height = 5
        val labels = IntArray(width * height) { 0 }
        labels[2 * width + 2] = 1
        val result = ImageMath.modeFilterLabels(labels, width, height, k = 2, passes = 1)
        assertTrue(result[2 * width + 2] == 0)
    }

    @Test
    fun paletteAssignmentUsesNearestColour() {
        val pixels = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF010101.toInt(),
        )
        val palette = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val indices = ImageMath.assignPalette(pixels, palette)
        assertTrue(indices[0] == 0)
        assertTrue(indices[1] == 1)
        assertTrue(indices[2] == 0)
    }
}
