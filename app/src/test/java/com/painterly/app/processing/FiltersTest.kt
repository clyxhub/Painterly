package com.painterly.app.processing

import com.painterly.app.model.Argb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    @Test
    fun boxBlurPreservesConstantField() {
        val source = FloatArray(100) { 40f }
        val result = Filters.boxBlur(source, 10, 10, 2)
        result.forEach { assertEquals(40f, it, 1e-3f) }
    }

    @Test
    fun luminanceOfWhiteIs255() {
        val result = Filters.luminance(intArrayOf(Argb.WHITE))
        assertEquals(255f, result[0], 0.5f)
    }

    @Test
    fun kMeansSeparatesTwoValueLevels() {
        val values = FloatArray(100) { if (it % 2 == 0) 20f else 200f }
        val (centers, labels) = Filters.kMeans1D(values, 2, 12)
        assertEquals(2, centers.size)
        assertEquals(labels[0], labels[2])
        assertNotEquals(labels[0], labels[1])
    }

    @Test
    fun dilateExpandsSinglePixel() {
        val mask = BooleanArray(25)
        mask[12] = true
        val result = Filters.dilate(mask, 5, 5, 1)
        assertTrue(result[12])
        assertTrue(result[11])
        assertTrue(result[13])
        assertTrue(result[7])
        assertTrue(result[17])
        assertFalse(result[0])
    }

    @Test
    fun removeSmallComponentsDropsNoise() {
        val mask = BooleanArray(100)
        mask[0] = true
        mask[1] = true
        mask[55] = true
        val result = Filters.removeSmallComponents(mask, 10, 10, 2)
        assertTrue(result[0])
        assertTrue(result[1])
        assertFalse(result[55])
    }

    @Test
    fun nonMaximumSuppressionKeepsStrongEdge() {
        val magnitude = FloatArray(25)
        magnitude[12] = 100f
        magnitude[11] = 10f
        magnitude[13] = 10f
        val direction = FloatArray(25)
        val result = Filters.nonMaximumSuppression(magnitude, direction, 5, 5)
        assertEquals(100f, result[12], 1e-3f)
        assertEquals(0f, result[11], 1e-3f)
    }
}
