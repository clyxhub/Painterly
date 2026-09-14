package com.painterly.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Flattens the progressive layer stack into a single preview bitmap.
 *
 * The order of [layers] is the paint order (first item lowest). Values are
 * drawn onto a solid [baseColor] canvas with per-layer opacity.
 */
object LayerCompositor {

    fun compose(
        width: Int,
        height: Int,
        baseColor: Int,
        layers: List<Pair<Bitmap, Float>>,
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(baseColor)
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = false
        }
        for ((bitmap, opacity) in layers) {
            if (bitmap.isRecycled) continue
            paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }
        return output
    }
}
