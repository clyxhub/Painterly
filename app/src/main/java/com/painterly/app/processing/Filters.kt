package com.painterly.app.processing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure-Kotlin, dependency-free image analysis primitives. Everything operates on
 * flat arrays so it can be unit tested on the JVM without Android.
 */
object Filters {

    class Sobel(val magnitude: FloatArray, val direction: FloatArray)

    /** Rec.709 luminance in the 0..255 range. */
    fun luminance(pixels: IntArray): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            out[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        return out
    }

    fun channel(pixels: IntArray, shift: Int): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) out[i] = ((pixels[i] ushr shift) and 0xFF).toFloat()
        return out
    }

    /** Separable, O(n) box blur with clamped edges. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val window = 2 * radius + 1
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)

        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (i in -radius..radius) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / window
                sum += src[row + (x + radius + 1).coerceIn(0, w - 1)]
                sum -= src[row + (x - radius).coerceIn(0, w - 1)]
            }
        }

        for (x in 0 until w) {
            var sum = 0f
            for (i in -radius..radius) sum += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / window
                sum += tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sum -= tmp[(y - radius).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    /** Three box passes approximate a Gaussian closely enough for our needs. */
    fun gaussianBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val r = radius.coerceAtLeast(1)
        var current = src
        repeat(3) { current = boxBlur(current, w, h, r) }
        return current
    }

    fun sobel(gray: FloatArray, w: Int, h: Int): Sobel {
        val magnitude = FloatArray(gray.size)
        val direction = FloatArray(gray.size)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val tl = gray[i - w - 1]; val tc = gray[i - w]; val tr = gray[i - w + 1]
                val ml = gray[i - 1]; val mr = gray[i + 1]
                val bl = gray[i + w - 1]; val bc = gray[i + w]; val br = gray[i + w + 1]
                val gx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val gy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)
                magnitude[i] = sqrt(gx * gx + gy * gy)
                direction[i] = atan2(gy, gx)
            }
        }
        return Sobel(magnitude, direction)
    }

    fun nonMaximumSuppression(magnitude: FloatArray, direction: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(magnitude.size)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val m = magnitude[i]
                if (m <= 0f) continue
                var angle = Math.toDegrees(direction[i].toDouble())
                if (angle < 0) angle += 180.0
                val bucket = (((angle + 22.5) / 45.0).toInt()) % 4
                val a: Float
                val b: Float
                when (bucket) {
                    0 -> { a = magnitude[i - 1]; b = magnitude[i + 1] }
                    1 -> { a = magnitude[i + w + 1]; b = magnitude[i - w - 1] }
                    2 -> { a = magnitude[i + w]; b = magnitude[i - w] }
                    else -> { a = magnitude[i + w - 1]; b = magnitude[i - w + 1] }
                }
                if (m >= a && m >= b) out[i] = m
            }
        }
        return out
    }

    fun hysteresis(magnitude: FloatArray, w: Int, h: Int, high: Float, low: Float): BooleanArray {
        val out = BooleanArray(magnitude.size)
        val stack = IntArray(magnitude.size)
        var top = 0
        for (i in magnitude.indices) {
            if (magnitude[i] >= high) {
                out[i] = true
                stack[top++] = i
            }
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % w
            val y = i / w
            for (dy in -1..1) {
                val ny = y + dy
                if (ny < 0 || ny >= h) continue
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx < 0 || nx >= w) continue
                    val j = ny * w + nx
                    if (!out[j] && magnitude[j] >= low) {
                        out[j] = true
                        stack[top++] = j
                    }
                }
            }
        }
        return out
    }

    fun percentile(values: FloatArray, p: Float, sampleStride: Int): Float {
        val stride = sampleStride.coerceAtLeast(1)
        val sample = ArrayList<Float>(values.size / stride + 1)
        var i = 0
        while (i < values.size) {
            sample.add(values[i])
            i += stride
        }
        if (sample.isEmpty()) return 0f
        sample.sort()
        val index = ((sample.size - 1) * p.coerceIn(0f, 1f)).toInt().coerceIn(0, sample.size - 1)
        return sample[index]
    }

    fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        var current = mask
        repeat(radius.coerceAtLeast(0)) { current = grow(current, w, h) }
        return current
    }

    fun erode(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        var current = mask
        repeat(radius.coerceAtLeast(0)) { current = shrink(current, w, h) }
        return current
    }

    private fun grow(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if (mask[i]) { out[i] = true; continue }
                if (x > 0 && mask[i - 1]) { out[i] = true; continue }
                if (x < w - 1 && mask[i + 1]) { out[i] = true; continue }
                if (y > 0 && mask[i - w]) { out[i] = true; continue }
                if (y < h - 1 && mask[i + w]) { out[i] = true; continue }
            }
        }
        return out
    }

    private fun shrink(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if (!mask[i]) continue
                val keep = (x == 0 || mask[i - 1]) &&
                    (x == w - 1 || mask[i + 1]) &&
                    (y == 0 || mask[i - w]) &&
                    (y == h - 1 || mask[i + w])
                out[i] = keep
            }
        }
        return out
    }

    fun open(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray =
        dilate(erode(mask, w, h, radius), w, h, radius)

    fun close(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray =
        erode(dilate(mask, w, h, radius), w, h, radius)

    /** Separable maximum filter (grayscale dilation). */
    fun dilateGray(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = src[row + x]
                for (k in -radius..radius) {
                    val v = src[row + (x + k).coerceIn(0, w - 1)]
                    if (v > m) m = v
                }
                tmp[row + x] = m
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var m = tmp[y * w + x]
                for (k in -radius..radius) {
                    val v = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    if (v > m) m = v
                }
                out[y * w + x] = m
            }
        }
        return out
    }

    fun removeSmallComponents(mask: BooleanArray, w: Int, h: Int, minSize: Int): BooleanArray {
        if (minSize <= 1) return mask.copyOf()
        val labels = componentLabels(mask, w, h)
        if (labels.sizes.isEmpty()) return BooleanArray(mask.size)
        val out = BooleanArray(mask.size)
        for (i in mask.indices) {
            val label = labels.map[i]
            if (label >= 0 && labels.sizes[label] >= minSize) out[i] = true
        }
        return out
    }

    fun keepTopNComponents(mask: BooleanArray, w: Int, h: Int, n: Int): BooleanArray {
        if (n <= 0) return BooleanArray(mask.size)
        val labels = componentLabels(mask, w, h)
        if (labels.sizes.size <= n) return mask.copyOf()
        val threshold = labels.sizes.sortedDescending()[n - 1]
        val out = BooleanArray(mask.size)
        for (i in mask.indices) {
            val label = labels.map[i]
            if (label >= 0 && labels.sizes[label] >= threshold) out[i] = true
        }
        return out
    }

    class ComponentMap(val map: IntArray, val sizes: IntArray)

    fun componentLabels(mask: BooleanArray, w: Int, h: Int): ComponentMap {
        val map = IntArray(mask.size) { -1 }
        val queue = IntArray(mask.size)
        val sizes = ArrayList<Int>()
        var label = 0
        for (start in mask.indices) {
            if (!mask[start] || map[start] != -1) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            map[start] = label
            var count = 0
            while (head < tail) {
                val i = queue[head++]
                count++
                val x = i % w
                val y = i / w
                if (x > 0) {
                    val j = i - 1
                    if (mask[j] && map[j] == -1) { map[j] = label; queue[tail++] = j }
                }
                if (x < w - 1) {
                    val j = i + 1
                    if (mask[j] && map[j] == -1) { map[j] = label; queue[tail++] = j }
                }
                if (y > 0) {
                    val j = i - w
                    if (mask[j] && map[j] == -1) { map[j] = label; queue[tail++] = j }
                }
                if (y < h - 1) {
                    val j = i + w
                    if (mask[j] && map[j] == -1) { map[j] = label; queue[tail++] = j }
                }
            }
            sizes.add(count)
            label++
        }
        return ComponentMap(map, sizes.toIntArray())
    }

    fun majorityFilter(labels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return labels
        val out = IntArray(labels.size)
        val counts = HashMap<Int, Int>()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                counts.clear()
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    val nrow = ny * w
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val label = labels[nrow + nx]
                        counts[label] = (counts[label] ?: 0) + 1
                    }
                }
                var best = labels[row + x]
                var bestCount = -1
                for ((label, count) in counts) {
                    if (count > bestCount) { bestCount = count; best = label }
                }
                out[row + x] = best
            }
        }
        return out
    }

    /** 1D k-means over luminance, returning cluster centres and per-pixel labels. */
    fun kMeans1D(values: FloatArray, k: Int, iterations: Int): Pair<FloatArray, IntArray> {
        val clusters = k.coerceAtLeast(1)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (v in values) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val centers = FloatArray(clusters) { min + (max - min) * (it + 0.5f) / clusters }
        val labels = IntArray(values.size)
        if (max - min < 1e-3f) return centers to labels
        repeat(iterations) {
            for (i in values.indices) {
                var best = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until clusters) {
                    val d = abs(values[i] - centers[c])
                    if (d < bestDist) { bestDist = d; best = c }
                }
                labels[i] = best
            }
            val sums = FloatArray(clusters)
            val counts = IntArray(clusters)
            for (i in values.indices) {
                sums[labels[i]] += values[i]
                counts[labels[i]]++
            }
            for (c in 0 until clusters) {
                if (counts[c] > 0) centers[c] = sums[c] / counts[c]
            }
        }
        return centers to labels
    }
}
