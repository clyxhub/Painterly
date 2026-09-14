package com.painterly.app.imaging

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic, dependency-free image math shared by every stage.
 *
 * Images are plain ARGB `IntArray`s. Single-channel images are `FloatArray`s in 0..255.
 * Everything is conventional image processing — no AI, no network.
 */
object ImageMath {

    fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return if (x < edge0) 0f else 1f
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3f - 2f * t)
    }

    fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun toLuminance(pixels: IntArray): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) out[i] = luminance(pixels[i])
        return out
    }

    // ---------------------------------------------------------------- blurring

    /** Separable box blur with edge clamping. O(n) per pass. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0 || w == 0 || h == 0) return src.copyOf()
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val win = (radius * 2 + 1).toFloat()

        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (i in -radius..radius) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                val outX = (x - radius).coerceIn(0, w - 1)
                val inX = (x + radius + 1).coerceIn(0, w - 1)
                sum += src[row + inX] - src[row + outX]
            }
        }

        for (x in 0 until w) {
            var sum = 0f
            for (i in -radius..radius) sum += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                val outY = (y - radius).coerceIn(0, h - 1)
                val inY = (y + radius + 1).coerceIn(0, h - 1)
                sum += tmp[inY * w + x] - tmp[outY * w + x]
            }
        }
        return out
    }

    /** Gaussian blur approximated with three box passes (fast and stable). */
    fun gaussianBlur(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma <= 0.01f || w == 0 || h == 0) return src.copyOf()
        val boxes = boxesForGauss(sigma, 3)
        var cur = src
        for (b in boxes) cur = boxBlur(cur, w, h, ((b - 1) / 2).coerceAtLeast(0))
        return cur
    }

    private fun boxesForGauss(sigma: Float, n: Int): IntArray {
        val wIdeal = sqrt((12.0 * sigma * sigma / n) + 1.0)
        var wl = floor(wIdeal).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12.0 * sigma * sigma - n * wl * wl - 4.0 * n * wl - 3.0 * n) /
            (-4.0 * wl - 4.0)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    /** Blur each colour channel, returning a smoothed ARGB image. */
    fun blurArgb(pixels: IntArray, w: Int, h: Int, sigma: Float): IntArray {
        if (sigma <= 0.01f) return pixels.copyOf()
        val r = channel(pixels, 16)
        val g = channel(pixels, 8)
        val b = channel(pixels, 0)
        val rb = gaussianBlur(r, w, h, sigma)
        val gb = gaussianBlur(g, w, h, sigma)
        val bb = gaussianBlur(b, w, h, sigma)
        val out = IntArray(pixels.size)
        for (i in out.indices) {
            val ri = rb[i].roundToInt().coerceIn(0, 255)
            val gi = gb[i].roundToInt().coerceIn(0, 255)
            val bi = bb[i].roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        return out
    }

    private fun channel(pixels: IntArray, shift: Int): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) out[i] = ((pixels[i] shr shift) and 0xFF).toFloat()
        return out
    }

    // ---------------------------------------------------------------- gradients

    fun gradient(gray: FloatArray, w: Int, h: Int): Pair<FloatArray, FloatArray> {
        val gx = FloatArray(gray.size)
        val gy = FloatArray(gray.size)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                gx[i] = -gray[i - w - 1] - 2f * gray[i - 1] - gray[i + w - 1] +
                    gray[i - w + 1] + 2f * gray[i + 1] + gray[i + w + 1]
                gy[i] = -gray[i - w - 1] - 2f * gray[i - w] - gray[i - w + 1] +
                    gray[i + w - 1] + 2f * gray[i + w] + gray[i + w + 1]
            }
        }
        return gx to gy
    }

    fun magnitude(gx: FloatArray, gy: FloatArray): FloatArray {
        val out = FloatArray(gx.size)
        for (i in out.indices) out[i] = sqrt(gx[i] * gx[i] + gy[i] * gy[i])
        return out
    }

    /**
     * Canny-style structural edge map: Sobel, non-maximum suppression, hysteresis
     * thresholding, then small-component removal so texture noise disappears.
     */
    fun cannyEdges(
        gray: FloatArray,
        w: Int,
        h: Int,
        low: Float,
        high: Float,
        minComponent: Int,
    ): BooleanArray {
        val (gx, gy) = gradient(gray, w, h)
        val mag = magnitude(gx, gy)
        val suppressed = FloatArray(gray.size)

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val m = mag[i]
                if (m <= 0f) continue
                var angle = Math.toDegrees(atan2(gy[i].toDouble(), gx[i].toDouble())).toFloat()
                if (angle < 0f) angle += 180f
                val (n1, n2) = when {
                    angle < 22.5f || angle >= 157.5f -> mag[i - 1] to mag[i + 1]
                    angle < 67.5f -> mag[i - w + 1] to mag[i + w - 1]
                    angle < 112.5f -> mag[i - w] to mag[i + w]
                    else -> mag[i - w - 1] to mag[i + w + 1]
                }
                suppressed[i] = if (m >= n1 && m >= n2) m else 0f
            }
        }

        val edge = BooleanArray(gray.size)
        val stack = ArrayDeque<Int>()
        for (i in gray.indices) {
            if (suppressed[i] >= high) {
                edge[i] = true
                stack.addLast(i)
            }
        }
        val weak = BooleanArray(gray.size)
        for (i in gray.indices) weak[i] = suppressed[i] >= low

        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w
            val y = i / w
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 1 until w - 1 && ny in 1 until h - 1) {
                        val j = ny * w + nx
                        if (weak[j] && !edge[j]) {
                            edge[j] = true
                            stack.addLast(j)
                        }
                    }
                }
            }
        }

        if (minComponent > 1) removeSmallComponents(edge, w, h, minComponent)
        return edge
    }

    // ---------------------------------------------------------------- morphology

    fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        val tmp = BooleanArray(mask.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w - 1)
                var hit = false
                var xx = x0
                while (xx <= x1) {
                    if (mask[row + xx]) {
                        hit = true
                        break
                    }
                    xx++
                }
                tmp[row + x] = hit
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                val y0 = (y - radius).coerceAtLeast(0)
                val y1 = (y + radius).coerceAtMost(h - 1)
                var hit = false
                var yy = y0
                while (yy <= y1) {
                    if (tmp[yy * w + x]) {
                        hit = true
                        break
                    }
                    yy++
                }
                out[y * w + x] = hit
            }
        }
        return out
    }

    fun erode(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        val tmp = BooleanArray(mask.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w - 1)
                var keep = true
                var xx = x0
                while (xx <= x1) {
                    if (!mask[row + xx]) {
                        keep = false
                        break
                    }
                    xx++
                }
                tmp[row + x] = keep
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                val y0 = (y - radius).coerceAtLeast(0)
                val y1 = (y + radius).coerceAtMost(h - 1)
                var keep = true
                var yy = y0
                while (yy <= y1) {
                    if (!tmp[yy * w + x]) {
                        keep = false
                        break
                    }
                    yy++
                }
                out[y * w + x] = keep
            }
        }
        return out
    }

    private fun keepComponents(
        mask: BooleanArray,
        w: Int,
        h: Int,
        predicate: (Int) -> Boolean,
    ) {
        val visited = BooleanArray(mask.size)
        val stack = ArrayDeque<Int>()
        val comp = ArrayList<Int>()
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            comp.clear()
            stack.clear()
            stack.addLast(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                comp.add(i)
                val x = i % w
                val y = i / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h) {
                            val j = ny * w + nx
                            if (mask[j] && !visited[j]) {
                                visited[j] = true
                                stack.addLast(j)
                            }
                        }
                    }
                }
            }
            if (!predicate(comp.size)) for (i in comp) mask[i] = false
        }
    }

    fun removeSmallComponents(mask: BooleanArray, w: Int, h: Int, minSize: Int) {
        if (minSize <= 1) return
        keepComponents(mask, w, h) { it >= minSize }
    }

    fun removeLargeComponents(mask: BooleanArray, w: Int, h: Int, maxSize: Int) {
        if (maxSize <= 0) return
        keepComponents(mask, w, h) { it <= maxSize }
    }

    // ---------------------------------------------------------------- colour

    /**
     * k-means palette over a downscaled copy of the image, returned as ARGB colours.
     * Seeded from luminance percentiles so results are deterministic.
     */
    fun kMeansPalette(
        pixels: IntArray,
        w: Int,
        h: Int,
        k: Int,
        iterations: Int,
    ): IntArray {
        val sample = samplePixels(pixels, w, h, 220)
        if (sample.isEmpty()) return IntArray(k) { 0xFF808080.toInt() }
        val (sr, sg, sb) = channelTriples(sample)
        val lum = FloatArray(sample.size) { luminance(sample[it]) }
        val order = lum.indices.sortedBy { lum[it] }

        val centers = Array(k) { FloatArray(3) }
        for (c in 0 until k) {
            val idx = order[((c + 0.5f) / k * order.size).toInt().coerceIn(0, order.size - 1)]
            centers[c][0] = sr[idx]
            centers[c][1] = sg[idx]
            centers[c][2] = sb[idx]
        }

        val sums = Array(k) { DoubleArray(3) }
        val counts = IntArray(k)
        repeat(iterations) {
            for (c in 0 until k) {
                sums[c].fill(0.0)
                counts[c] = 0
            }
            for (p in sample.indices) {
                var best = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until k) {
                    val dr = sr[p] - centers[c][0]
                    val dg = sg[p] - centers[c][1]
                    val db = sb[p] - centers[c][2]
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestDist) {
                        bestDist = d
                        best = c
                    }
                }
                sums[best][0] += sr[p]
                sums[best][1] += sg[p]
                sums[best][2] += sb[p]
                counts[best]++
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    centers[c][0] = (sums[c][0] / counts[c]).toFloat()
                    centers[c][1] = (sums[c][1] / counts[c]).toFloat()
                    centers[c][2] = (sums[c][2] / counts[c]).toFloat()
                }
            }
        }

        // Order palette from dark to light so value families read predictably.
        return centers.sortedBy { 0.2126f * it[0] + 0.7152f * it[1] + 0.0722f * it[2] }
            .map { c ->
                val r = c[0].roundToInt().coerceIn(0, 255)
                val g = c[1].roundToInt().coerceIn(0, 255)
                val b = c[2].roundToInt().coerceIn(0, 255)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }.toIntArray()
    }

    /** Assign every pixel to its nearest palette colour, returning palette indices. */
    fun assignPalette(pixels: IntArray, palette: IntArray): IntArray {
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (p in palette.indices) {
                val pc = palette[p]
                val dr = r - ((pc shr 16) and 0xFF)
                val dg = g - ((pc shr 8) and 0xFF)
                val db = b - (pc and 0xFF)
                val d = dr * dr + dg * dg + db * db
                if (d < bestDist) {
                    bestDist = d
                    best = p
                }
            }
            out[i] = best
        }
        return out
    }

    /** Majority (mode) filter over label indices — removes speckle and cleans region edges. */
    fun modeFilterLabels(labels: IntArray, w: Int, h: Int, k: Int, passes: Int): IntArray {
        var current = labels
        val counts = IntArray(k)
        repeat(passes) {
            val next = IntArray(current.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    counts.fill(0)
                    for (dy in -1..1) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, w - 1)
                            counts[current[ny * w + nx]]++
                        }
                    }
                    var best = 0
                    var bestCount = -1
                    for (c in 0 until k) {
                        if (counts[c] > bestCount) {
                            bestCount = counts[c]
                            best = c
                        }
                    }
                    next[y * w + x] = best
                }
            }
            current = next
        }
        return current
    }

    fun labelsToPixels(labels: IntArray, palette: IntArray): IntArray {
        val out = IntArray(labels.size)
        for (i in out.indices) out[i] = palette[labels[i]]
        return out
    }

    // ---------------------------------------------------------------- helpers

    /** Value at the given fraction (0..1) of a 0..255 histogram. */
    fun percentileValue(values: FloatArray, fraction: Float): Float {
        val hist = IntArray(256)
        var total = 0
        for (v in values) {
            val b = v.roundToInt().coerceIn(0, 255)
            hist[b]++
            total++
        }
        if (total == 0) return 0f
        val target = (total * fraction.coerceIn(0f, 1f)).toInt()
        var acc = 0
        for (b in 0 until 256) {
            acc += hist[b]
            if (acc >= target) return b.toFloat()
        }
        return 255f
    }

    private fun samplePixels(pixels: IntArray, w: Int, h: Int, maxDim: Int): IntArray {
        val step = max(1, max(w, h) / maxDim)
        if (step == 1) return pixels
        val out = ArrayList<Int>(pixels.size / (step * step) + 1)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                out.add(pixels[y * w + x])
                x += step
            }
            y += step
        }
        return out.toIntArray()
    }

    private fun channelTriples(pixels: IntArray): Triple<FloatArray, FloatArray, FloatArray> {
        val r = FloatArray(pixels.size)
        val g = FloatArray(pixels.size)
        val b = FloatArray(pixels.size)
        for (i in pixels.indices) {
            r[i] = ((pixels[i] shr 16) and 0xFF).toFloat()
            g[i] = ((pixels[i] shr 8) and 0xFF).toFloat()
            b[i] = (pixels[i] and 0xFF).toFloat()
        }
        return Triple(r, g, b)
    }

    fun previewCount(mask: BooleanArray): Int {
        var n = 0
        for (m in mask) if (m) n++
        return n
    }

    fun absDiff(a: Float, b: Float): Float = abs(a - b)
}
