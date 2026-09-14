package com.painterly.app.processing

import com.painterly.app.model.Argb
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic, offline generators for every paintable layer.
 *
 * Each function takes source pixels (premultiplied-free ARGB ints) and returns
 * an ARGB layer where fully transparent pixels mean "this stage adds nothing
 * here". Layers are pure data so they can be unit tested and cached without
 * holding a Bitmap.
 */
object StageGenerators {

    fun generate(layer: LayerId, src: IntArray, w: Int, h: Int, params: StageParameters): IntArray =
        when (layer) {
            LayerId.DRAWING -> drawing(
                src, w, h,
                detail = params.drawingDetail,
                sensitivity = params.drawingSensitivity,
                simplification = params.drawingSimplification,
            )
            LayerId.BLOCK_IN -> blockIn(src, w, h, params.blockInValues, params.blockInSimplification)
            LayerId.SHADOW_MASS -> shadowMass(src, w, h, params.shadowAmount)
            LayerId.LIGHT_MASS -> lightMass(src, w, h, params.lightAmount)
            LayerId.CORE_SHADOWS -> coreShadows(src, w, h, params.coreAmount)
            LayerId.ACCENTS -> accents(src, w, h, params.accentStrength, params.accentCount)
            LayerId.REFINE_EDGE -> refineEdgeControl(src, w, h, params.refinement.edgeControl)
            LayerId.REFINE_DETAIL -> refineDetailPriority(src, w, h, params.refinement.detailPriority)
            LayerId.REFINE_TEXTURE -> refineTexture(src, w, h, params.refinement.texture)
            LayerId.REFINE_COLOR -> refineColour(src, w, h, params.refinement.colourVariation)
        }

    // ---------------------------------------------------------------- Stage 1

    fun drawing(
        src: IntArray,
        w: Int,
        h: Int,
        detail: Float,
        sensitivity: Float,
        simplification: Float,
    ): IntArray {
        val d = detail.coerceIn(0f, 1f)
        val s = sensitivity.coerceIn(0f, 1f)
        val simpl = simplification.coerceIn(0f, 1f)

        val gray = Filters.luminance(src)
        val blurRadius = (4 - d * 3f).toInt().coerceIn(1, 4)
        val blurred = Filters.gaussianBlur(gray, w, h, blurRadius)
        val sobel = Filters.sobel(blurred, w, h)
        val high = Filters.percentile(sobel.magnitude, 0.90f - s * 0.35f, 2).coerceAtLeast(6f)
        val low = high * 0.45f
        val nms = Filters.nonMaximumSuppression(sobel.magnitude, sobel.direction, w, h)
        val edges = Filters.hysteresis(nms, w, h, high, low)

        val minSize = (w * h * (0.00008f + simpl * 0.0016f)).toInt().coerceAtLeast(2)
        val cleaned = Filters.removeSmallComponents(edges, w, h, minSize)

        val line = Argb.pack(255, 28, 26, 30)
        return IntArray(w * h) { if (cleaned[it]) line else Argb.TRANSPARENT }
    }

    // ---------------------------------------------------------------- Stage 2

    fun blockIn(src: IntArray, w: Int, h: Int, values: Int, simplification: Float): IntArray {
        val k = values.coerceIn(3, 4)
        val simpl = simplification.coerceIn(0f, 1f)
        val blurRadius = (1 + simpl * 5f).toInt().coerceIn(1, 6)

        val red = Filters.boxBlur(Filters.channel(src, 16), w, h, blurRadius)
        val green = Filters.boxBlur(Filters.channel(src, 8), w, h, blurRadius)
        val blue = Filters.boxBlur(Filters.channel(src, 0), w, h, blurRadius)
        val luminance = FloatArray(w * h) {
            0.2126f * red[it] + 0.7152f * green[it] + 0.0722f * blue[it]
        }

        val labels = Filters.kMeans1D(luminance, k, 10).second
        val sumR = FloatArray(k)
        val sumG = FloatArray(k)
        val sumB = FloatArray(k)
        val counts = IntArray(k)
        for (i in 0 until w * h) {
            val label = labels[i]
            sumR[label] += red[i]
            sumG[label] += green[i]
            sumB[label] += blue[i]
            counts[label]++
        }
        val representative = IntArray(k) { index ->
            val count = counts[index].coerceAtLeast(1)
            Argb.pack(
                255,
                (sumR[index] / count).toInt(),
                (sumG[index] / count).toInt(),
                (sumB[index] / count).toInt(),
            )
        }

        val merged = Filters.majorityFilter(labels, w, h, (simpl * 2.5f).toInt().coerceIn(0, 2))
        return IntArray(w * h) { representative[merged[it]] }
    }

    // ---------------------------------------------------------------- Stage 3

    fun shadowMass(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val radius = (min(w, h) / 12).coerceIn(6, 48)
        val local = Filters.boxBlur(gray, w, h, radius)
        val global = gray.average().toFloat()
        val threshold = 0.98f - a * 0.5f

        val mask = BooleanArray(w * h)
        for (i in gray.indices) {
            val ratio = gray[i] / (local[i] + 1e-3f)
            mask[i] = ratio < threshold || gray[i] < global * 0.55f
        }
        val cleaned = Filters.close(Filters.open(mask, w, h, 1), w, h, 1)
        val minSize = (w * h * 0.0004f).toInt().coerceAtLeast(4)
        val filtered = Filters.removeSmallComponents(cleaned, w, h, minSize)

        return IntArray(w * h) { i ->
            if (filtered[i]) {
                val darkness = (1f - gray[i] / 255f).coerceIn(0f, 1f)
                Argb.pack((120 + 120 * darkness).toInt(), 36, 44, 62)
            } else {
                Argb.TRANSPARENT
            }
        }
    }

    // ---------------------------------------------------------------- Stage 4

    fun lightMass(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val radius = (min(w, h) / 12).coerceIn(6, 48)
        val local = Filters.boxBlur(gray, w, h, radius)
        val global = gray.average().toFloat()
        val threshold = 1.04f - a * 0.5f

        val mask = BooleanArray(w * h)
        for (i in gray.indices) {
            val ratio = gray[i] / (local[i] + 1e-3f)
            mask[i] = ratio > threshold || gray[i] > global * 1.35f
        }
        val cleaned = Filters.close(Filters.open(mask, w, h, 1), w, h, 1)
        val minSize = (w * h * 0.0004f).toInt().coerceAtLeast(4)
        val filtered = Filters.removeSmallComponents(cleaned, w, h, minSize)

        return IntArray(w * h) { i ->
            if (filtered[i]) {
                val brightness = (gray[i] / 255f).coerceIn(0f, 1f)
                Argb.pack((100 + 120 * brightness).toInt(), 255, 244, 212)
            } else {
                Argb.TRANSPARENT
            }
        }
    }

    // ---------------------------------------------------------------- Stage 5

    fun coreShadows(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val radius = (min(w, h) / 14).coerceIn(6, 40)
        val local = Filters.boxBlur(gray, w, h, radius)

        val combined = BooleanArray(w * h)
        for (i in gray.indices) {
            val ratio = gray[i] / (local[i] + 1e-3f)
            combined[i] = ratio < 0.9f || ratio > 1.12f
        }
        val bandRadius = (2 + a * 4f).toInt().coerceIn(2, 6)
        val dilated = Filters.dilate(combined, w, h, bandRadius)
        val band = BooleanArray(w * h) { dilated[it] && !combined[it] }

        val minSize = (w * h * (0.0016f - a * 0.0013f)).toInt().coerceAtLeast(3)
        val filtered = Filters.removeSmallComponents(band, w, h, minSize)

        return IntArray(w * h) { if (filtered[it]) Argb.pack(165, 120, 98, 86) else Argb.TRANSPARENT }
    }

    // ---------------------------------------------------------------- Stage 6

    fun accents(src: IntArray, w: Int, h: Int, strength: Float, count: Int): IntArray {
        val a = strength.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val radius = (min(w, h) / 20).coerceIn(4, 32)
        val localMax = Filters.dilateGray(gray, w, h, radius)
        val threshold = Filters.percentile(gray, 0.96f - a * 0.30f, 3)
        val prominence = 6f

        val mask = BooleanArray(w * h) {
            gray[it] >= localMax[it] - prominence && gray[it] >= threshold
        }
        val minSize = (w * h * (0.00003f + (1f - a) * 0.00012f)).toInt().coerceAtLeast(2)
        val cleaned = Filters.removeSmallComponents(mask, w, h, minSize)
        val limited = Filters.keepTopNComponents(cleaned, w, h, count)

        return IntArray(w * h) { i ->
            if (limited[i]) {
                val alpha = (175 + 70 * (gray[i] / 255f)).toInt()
                Argb.pack(alpha, 255, 252, 240)
            } else {
                Argb.TRANSPARENT
            }
        }
    }

    // ---------------------------------------------------------------- Stage 7

    fun refineEdgeControl(src: IntArray, w: Int, h: Int, sensitivity: Float): IntArray {
        val s = sensitivity.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val blurred = Filters.gaussianBlur(gray, w, h, 2)
        val sobel = Filters.sobel(blurred, w, h)
        val nms = Filters.nonMaximumSuppression(sobel.magnitude, sobel.direction, w, h)

        val strong = Filters.percentile(nms, 0.985f - s * 0.02f, 3).coerceAtLeast(5f)
        val softThreshold = strong * 0.45f
        val lostThreshold = strong * 0.18f

        var hard = BooleanArray(w * h)
        var soft = BooleanArray(w * h)
        val lost = BooleanArray(w * h)
        for (i in nms.indices) {
            val m = nms[i]
            when {
                m >= strong -> hard[i] = true
                m >= softThreshold -> soft[i] = true
                m >= lostThreshold -> lost[i] = true
            }
        }
        val minSize = (w * h * 0.00002f).toInt().coerceAtLeast(2)
        hard = Filters.removeSmallComponents(hard, w, h, minSize)
        soft = Filters.removeSmallComponents(soft, w, h, minSize)

        val hardColor = Argb.pack(225, 226, 74, 80)
        val softColor = Argb.pack(150, 245, 166, 35)
        val lostColor = Argb.pack(70, 74, 144, 217)
        return IntArray(w * h) { i ->
            when {
                hard[i] -> hardColor
                soft[i] -> softColor
                lost[i] -> lostColor
                else -> Argb.TRANSPARENT
            }
        }
    }

    fun refineDetailPriority(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val edges = Filters.sobel(Filters.gaussianBlur(gray, w, h, 1), w, h).magnitude
        val edgeThreshold = Filters.percentile(edges, 0.88f, 3).coerceAtLeast(4f)

        val block = (min(w, h) / 22).coerceIn(8, 40)
        val blocksX = (w + block - 1) / block
        val blocksY = (h + block - 1) / block
        val cells = blocksX * blocksY
        val sum = FloatArray(cells)
        val sumSq = FloatArray(cells)
        val edgeCount = IntArray(cells)

        for (y in 0 until h) {
            val by = (y / block) * blocksX
            for (x in 0 until w) {
                val i = y * w + x
                val cell = by + (x / block)
                val v = gray[i]
                sum[cell] += v
                sumSq[cell] += v * v
                if (edges[i] >= edgeThreshold) edgeCount[cell]++
            }
        }

        val count = (block * block).toFloat()
        val score = FloatArray(cells) { cell ->
            val mean = sum[cell] / count
            val variance = max(0f, sumSq[cell] / count - mean * mean)
            val density = edgeCount[cell] / count
            density * 0.75f + (sqrt(variance) / 64f).coerceIn(0f, 1f) * 0.6f
        }

        val minScore = score.minOrNull() ?: 0f
        val maxScore = score.maxOrNull() ?: 0f
        val range = maxScore - minScore
        val highThreshold = minScore + range * (0.62f - a * 0.35f)
        val mediumThreshold = minScore + range * (0.34f - a * 0.20f)

        val classes = IntArray(w * h)
        if (range < 1e-4f) {
            classes.fill(1)
        } else {
            for (i in classes.indices) {
                val cell = (i / w / block) * blocksX + (i % w / block)
                classes[i] = when {
                    score[cell] >= highThreshold -> 2
                    score[cell] >= mediumThreshold -> 1
                    else -> 0
                }
            }
        }
        val smoothed = Filters.boxBlur(FloatArray(w * h) { classes[it].toFloat() }, w, h, (block / 2).coerceAtLeast(1))

        return IntArray(w * h) { i ->
            when {
                smoothed[i] >= 1.6f -> Argb.pack(90, 214, 63, 140)
                smoothed[i] >= 0.85f -> Argb.pack(70, 240, 150, 60)
                else -> Argb.pack(45, 70, 130, 200)
            }
        }
    }

    fun refineTexture(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val gray = Filters.luminance(src)
        val scale = (min(w, h) / 40).coerceIn(2, 16)
        val low = Filters.gaussianBlur(gray, w, h, scale)
        val highFrequency = FloatArray(w * h) { abs(gray[it] - low[it]) }
        val energy = Filters.boxBlur(FloatArray(w * h) { highFrequency[it] * highFrequency[it] }, w, h, scale * 2)
        val threshold = Filters.percentile(energy, 0.55f - a * 0.25f, 3)

        return IntArray(w * h) { i ->
            val e = energy[i]
            when {
                e >= threshold * 2.2f -> Argb.pack(85, 63, 196, 180)
                e >= threshold -> Argb.pack(65, 120, 190, 120)
                else -> Argb.pack(40, 90, 110, 190)
            }
        }
    }

    fun refineColour(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val a = amount.coerceIn(0f, 1f)
        val red = Filters.channel(src, 16)
        val green = Filters.channel(src, 8)
        val blue = Filters.channel(src, 0)
        val redGreen = FloatArray(w * h) { red[it] - green[it] }
        val yellowBlue = FloatArray(w * h) { (red[it] + green[it]) * 0.5f - blue[it] }

        val fine = (min(w, h) / 60).coerceIn(2, 12)
        val coarse = (min(w, h) / 14).coerceIn(8, 48)
        val rgFine = Filters.boxBlur(redGreen, w, h, fine)
        val rgCoarse = Filters.boxBlur(redGreen, w, h, coarse)
        val ybFine = Filters.boxBlur(yellowBlue, w, h, fine)
        val ybCoarse = Filters.boxBlur(yellowBlue, w, h, coarse)

        val magnitude = FloatArray(w * h) {
            val dRg = rgFine[it] - rgCoarse[it]
            val dYb = ybFine[it] - ybCoarse[it]
            sqrt(dRg * dRg + dYb * dYb)
        }
        val threshold = Filters.percentile(magnitude, 0.90f - a * 0.25f, 3).coerceAtLeast(2f)

        val out = IntArray(w * h)
        for (i in out.indices) {
            val m = magnitude[i]
            if (m < threshold) continue
            val warm = (rgFine[i] - rgCoarse[i]) >= 0f
            val alpha = (60 + 90 * min(1f, m / (threshold * 3f))).toInt()
            out[i] = if (warm) Argb.pack(alpha, 235, 110, 80) else Argb.pack(alpha, 80, 170, 210)
        }
        return out
    }
}
