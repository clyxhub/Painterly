package com.painterly.app.imaging

import android.graphics.Bitmap
import com.painterly.app.model.PaintingStage
import com.painterly.app.model.RefineView
import com.painterly.app.model.StageSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared, stage-independent intermediates. Computed once per block-in value count and
 * reused by every later stage so only what changed is recomputed.
 */
class SharedIntermediates(
    val width: Int,
    val height: Int,
    val rawGray: FloatArray,
    val gray: FloatArray,
    val palette: IntArray,
    val labels: IntArray,
    val blockPixels: IntArray,
)

/**
 * Deterministic generation of the seven progressive painting stages. Each stage returns a
 * full-size ARGB layer whose transparent pixels mean "nothing new here"; compositing the
 * layers in stage order produces the cumulative painting.
 */
object StageGenerator {

    fun computeShared(reference: Bitmap, settings: StageSettings): SharedIntermediates {
        val w = reference.width
        val h = reference.height
        val pixels = IntArray(w * h)
        reference.getPixels(pixels, 0, w, 0, 0, w, h)

        val rawGray = ImageMath.toLuminance(pixels)
        val structuralSigma = (min(w, h) * 0.006f).coerceIn(1.2f, 4f)
        val gray = ImageMath.gaussianBlur(rawGray, w, h, structuralSigma)

        // A stronger, edge-aware-feeling smoothing before clustering keeps block-in regions
        // coherent instead of speckled (not a cheap posterize).
        val regionalSigma = (min(w, h) * 0.014f).coerceIn(2.5f, 9f)
        val smoothed = ImageMath.blurArgb(pixels, w, h, regionalSigma)
        val palette = ImageMath.kMeansPalette(smoothed, w, h, settings.blockInValues, 8)
        val assigned = ImageMath.assignPalette(smoothed, palette)
        val labels = ImageMath.modeFilterLabels(assigned, w, h, palette.size, 2)
        val blockPixels = ImageMath.labelsToPixels(labels, palette)

        return SharedIntermediates(w, h, rawGray, gray, palette, labels, blockPixels)
    }

    fun stage(
        stage: PaintingStage,
        shared: SharedIntermediates,
        settings: StageSettings,
    ): Bitmap = when (stage) {
        PaintingStage.DRAW -> draw(shared, settings)
        PaintingStage.BLOCK_IN -> blockIn(shared)
        PaintingStage.SHADOWS -> shadows(shared, settings)
        PaintingStage.LIGHTS -> lights(shared, settings)
        PaintingStage.FORM -> form(shared, settings)
        PaintingStage.ACCENTS -> accents(shared, settings)
        PaintingStage.REFINE -> refine(shared, settings)
    }

    fun generateAll(
        reference: Bitmap,
        settings: StageSettings,
        onProgress: (Float) -> Unit = {},
    ): Map<PaintingStage, Bitmap> {
        val shared = computeShared(reference, settings)
        val result = LinkedHashMap<PaintingStage, Bitmap>()
        val stages = PaintingStage.entries
        stages.forEachIndexed { index, stage ->
            result[stage] = stage(stage, shared, settings)
            onProgress((index + 1) / stages.size.toFloat())
        }
        return result
    }

    // ------------------------------------------------------------------ DRAW

    private fun draw(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val scale = (min(w, h) / 1000f).coerceIn(1f, 2.5f)
        val detail = settings.drawDetail

        val sigma = ImageMath.lerp(2.6f, 0.85f, detail) * scale
        val blur = ImageMath.gaussianBlur(shared.rawGray, w, h, sigma)
        val high = ImageMath.lerp(46f, 14f, detail)
        val low = high * 0.4f
        val minComponent = ImageMath.lerp(
            (min(w, h) * 0.02f).coerceAtLeast(40f),
            (min(w, h) * 0.002f).coerceAtLeast(6f),
            detail,
        ).toInt().coerceAtLeast(1)
        val edges = ImageMath.cannyEdges(blur, w, h, low, high, minComponent)
        val lineRadius = max(0, (min(w, h) / 900f).toInt())
        val rendered = if (lineRadius > 0) ImageMath.dilate(edges, w, h, lineRadius) else edges

        val ink = 0xFF2A2830.toInt()
        return bitmapFrom(w, h) { i -> if (rendered[i]) ink else 0 }
    }

    // ------------------------------------------------------------------ BLOCK-IN

    private fun blockIn(shared: SharedIntermediates): Bitmap {
        val out = IntArray(shared.blockPixels.size)
        for (i in out.indices) out[i] = shared.blockPixels[i] or (0xFF shl 24)
        return bitmapFromArray(shared.width, shared.height, out)
    }

    // ------------------------------------------------------------------ SHADOWS

    private fun shadows(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.shadowAmount
        val radius = (min(w, h) * 0.05f).roundToInt().coerceAtLeast(3)
        val localMean = ImageMath.boxBlur(shared.gray, w, h, radius)

        val relativeThreshold = ImageMath.lerp(26f, 6f, amount)
        val absoluteThreshold = ImageMath.percentileValue(shared.gray, ImageMath.lerp(0.10f, 0.34f, amount))

        var mask = BooleanArray(shared.gray.size)
        for (i in mask.indices) {
            mask[i] = (localMean[i] - shared.gray[i] > relativeThreshold) || (shared.gray[i] < absoluteThreshold)
        }
        mask = cleanMask(mask, w, h, (min(w, h) / 260f).toInt().coerceAtLeast(1), max(30, w * h / 6000))

        val darken = ImageMath.lerp(0.62f, 0.40f, amount)
        return bitmapFrom(w, h) { i ->
            if (mask[i]) shade(shared.blockPixels[i], darken, cool = 0.14f) else 0
        }
    }

    // ------------------------------------------------------------------ LIGHTS

    private fun lights(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.lightAmount
        val radius = (min(w, h) * 0.05f).roundToInt().coerceAtLeast(3)
        val localMean = ImageMath.boxBlur(shared.gray, w, h, radius)

        val relativeThreshold = ImageMath.lerp(30f, 8f, amount)
        val absoluteThreshold = ImageMath.percentileValue(shared.gray, ImageMath.lerp(0.90f, 0.66f, amount))

        var mask = BooleanArray(shared.gray.size)
        for (i in mask.indices) {
            mask[i] = (shared.gray[i] - localMean[i] > relativeThreshold) || (shared.gray[i] > absoluteThreshold)
        }
        mask = cleanMask(mask, w, h, (min(w, h) / 260f).toInt().coerceAtLeast(1), max(30, w * h / 6000))

        val lighten = ImageMath.lerp(0.30f, 0.55f, amount)
        return bitmapFrom(w, h) { i ->
            if (mask[i]) mixWhite(shared.blockPixels[i], lighten) else 0
        }
    }

    // ------------------------------------------------------------------ FORM

    private fun form(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.formAmount
        val s1 = (min(w, h) * 0.012f).coerceIn(2f, 6f)
        val near = ImageMath.gaussianBlur(shared.gray, w, h, s1)
        val far = ImageMath.gaussianBlur(shared.gray, w, h, s1 * 2.6f)
        val dog = FloatArray(shared.gray.size) { near[it] - far[it] }

        val signed = FloatArray(dog.size)
        val absValues = FloatArray(dog.size)
        for (i in dog.indices) {
            val lum = (shared.gray[i] / 255f).coerceIn(0f, 1f)
            val midtone = (1f - abs(lum - 0.5f) * 2f).coerceIn(0f, 1f)
            signed[i] = dog[i] * midtone
            absValues[i] = abs(signed[i])
        }
        val threshold = ImageMath.percentileValue(absValues, ImageMath.lerp(0.90f, 0.55f, amount))
        if (threshold <= 0.5f) return bitmapFrom(w, h) { 0 }

        return bitmapFrom(w, h) { i ->
            val v = signed[i]
            if (abs(v) < threshold) {
                0
            } else {
                val alpha = ImageMath.smoothstep(threshold, threshold * 2.6f, abs(v))
                val a = (alpha * 255f).roundToInt().coerceIn(0, 255) shl 24
                val base = if (v < 0f) {
                    shade(shared.blockPixels[i], 1f - 0.20f * alpha, cool = 0.05f * alpha)
                } else {
                    mixWhite(shared.blockPixels[i], 0.16f * alpha)
                }
                (base and 0x00FFFFFF) or a
            }
        }
    }

    // ------------------------------------------------------------------ ACCENTS

    private fun accents(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.accentAmount
        val radius = (min(w, h) * 0.02f).roundToInt().coerceAtLeast(2)
        val local = ImageMath.boxBlur(shared.gray, w, h, radius)

        val threshold = ImageMath.lerp(44f, 14f, amount)
        var mask = BooleanArray(shared.gray.size)
        for (i in mask.indices) {
            mask[i] = (shared.gray[i] - local[i] > threshold) && shared.gray[i] > 150f
        }
        val morph = (min(w, h) / 300f).toInt().coerceAtLeast(1)
        mask = openClose(mask, w, h, morph)
        ImageMath.removeLargeComponents(mask, w, h, max(140, w * h / 1200))
        ImageMath.removeSmallComponents(mask, w, h, max(2, w * h / 400000))

        return bitmapFrom(w, h) { i ->
            if (mask[i]) mixWhite(shared.blockPixels[i], 0.80f) else 0
        }
    }

    // ------------------------------------------------------------------ REFINE

    private fun refine(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        return when (settings.refineView) {
            RefineView.EDGES -> refineEdges(shared, settings)
            RefineView.DETAIL -> refineDetail(shared, settings)
        }
    }

    private fun refineEdges(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.refineAmount
        val smooth = ImageMath.gaussianBlur(shared.gray, w, h, (min(w, h) * 0.004f).coerceIn(1f, 2.5f))
        val (gx, gy) = ImageMath.gradient(smooth, w, h)
        val mag = ImageMath.magnitude(gx, gy)
        val high = ImageMath.percentileValue(mag, ImageMath.lerp(0.985f, 0.94f, amount))
        val softCut = high * 0.5f
        val lostCut = high * 0.35f

        val hard = BooleanArray(mag.size)
        val soft = BooleanArray(mag.size)
        val lost = BooleanArray(mag.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val m = mag[i]
                when {
                    m >= high -> hard[i] = true
                    m >= softCut -> soft[i] = true
                    else -> {
                        val label = shared.labels[i]
                        val boundary = label != shared.labels[i + 1] || label != shared.labels[i + w]
                        if (boundary && m < lostCut) lost[i] = true
                    }
                }
            }
        }

        val radius = max(0, (min(w, h) / 1200f).toInt())
        val hardR = if (radius > 0) ImageMath.dilate(hard, w, h, radius) else hard
        val softR = if (radius > 0) ImageMath.dilate(soft, w, h, radius) else soft

        val hardColor = 0xFFE23B2E.toInt()
        val softColor = 0xFFF0A93B.toInt()
        val lostColor = 0x4D8A8A8A
        return bitmapFrom(w, h) { i ->
            when {
                hardR[i] -> hardColor
                softR[i] -> softColor
                lost[i] -> lostColor
                else -> 0
            }
        }
    }

    private fun refineDetail(shared: SharedIntermediates, settings: StageSettings): Bitmap {
        val w = shared.width
        val h = shared.height
        val amount = settings.refineAmount
        val fine = ImageMath.gaussianBlur(shared.gray, w, h, 1.4f)
        val highFreq = FloatArray(shared.gray.size) { abs(shared.gray[it] - fine[it]) }
        val score = ImageMath.gaussianBlur(highFreq, w, h, (min(w, h) * 0.012f).coerceIn(2f, 7f))

        val highCut = ImageMath.percentileValue(score, ImageMath.lerp(0.90f, 0.72f, amount))
        val lowCut = ImageMath.percentileValue(score, ImageMath.lerp(0.6f, 0.35f, amount))
        val highColor = 0x99E8734A.toInt()
        val mediumColor = 0x66E0B84A.toInt()
        val lowColor = 0x404A7DE0

        return bitmapFrom(w, h) { i ->
            when {
                score[i] >= highCut -> highColor
                score[i] >= lowCut -> mediumColor
                else -> lowColor
            }
        }
    }

    // ------------------------------------------------------------------ rendering helpers

    private fun bitmapFrom(w: Int, h: Int, fill: (Int) -> Int): Bitmap {
        val arr = IntArray(w * h)
        for (i in arr.indices) arr[i] = fill(i)
        return bitmapFromArray(w, h, arr)
    }

    private fun bitmapFromArray(w: Int, h: Int, arr: IntArray): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(arr, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Multiply a colour toward darkness, optionally shifting it slightly cool. */
    private fun shade(argb: Int, factor: Float, cool: Float): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val nr = (r * factor).roundToInt().coerceIn(0, 255)
        val ng = (g * (factor - cool * 0.15f)).roundToInt().coerceIn(0, 255)
        val nb = (b * (factor + cool)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun mixWhite(argb: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val nr = (r + (255 - r) * t).roundToInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * t).roundToInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun cleanMask(
        mask: BooleanArray,
        w: Int,
        h: Int,
        radius: Int,
        minComponent: Int,
    ): BooleanArray {
        var m = ImageMath.erode(mask, w, h, radius)
        m = ImageMath.dilate(m, w, h, radius)
        ImageMath.removeSmallComponents(m, w, h, minComponent)
        m = ImageMath.dilate(m, w, h, radius)
        m = ImageMath.erode(m, w, h, radius)
        return m
    }

    private fun openClose(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask
        var m = ImageMath.erode(mask, w, h, radius)
        m = ImageMath.dilate(m, w, h, radius)
        m = ImageMath.dilate(m, w, h, radius)
        m = ImageMath.erode(m, w, h, radius)
        return m
    }
}
