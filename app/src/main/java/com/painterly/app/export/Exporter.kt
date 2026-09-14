package com.painterly.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.painterly.app.model.GridSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Composes the visible painting state at full working resolution and writes it to Pictures. */
object Exporter {

    val PAPER_ARGB: Int = 0xFFF4EEE3.toInt()

    fun compose(
        width: Int,
        height: Int,
        reference: Bitmap?,
        layers: List<Bitmap>,
        layerAlpha: Float,
        grid: GridSettings,
        paperColor: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(paperColor)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val full = Rect(0, 0, width, height)
        if (reference != null) canvas.drawBitmap(reference, null, full, paint)

        paint.alpha = (layerAlpha.coerceIn(0f, 1f) * 255f).toInt()
        for (layer in layers) canvas.drawBitmap(layer, null, full, paint)

        if (grid.enabled) drawGrid(canvas, width, height, grid)
        return out
    }

    fun exportToPictures(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Painterly",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: return null
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun timestampedName(prefix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Painterly_${prefix}_$stamp.png"
    }

    private fun drawGrid(canvas: Canvas, width: Int, height: Int, grid: GridSettings) {
        val opacity = (grid.opacity.coerceIn(0.05f, 1f) * 255f).toInt()
        val line = Paint().apply {
            color = Color.argb(opacity, 30, 28, 34)
            strokeWidth = (min(width, height) / 420f).coerceAtLeast(1.5f)
            isAntiAlias = true
        }
        val text = Paint().apply {
            color = Color.argb((grid.opacity.coerceIn(0.05f, 1f) * 255f).toInt(), 30, 28, 34)
            textSize = min(width, height) * 0.030f
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (c in 1 until grid.columns) {
            val x = width.toFloat() * c / grid.columns
            canvas.drawLine(x, 0f, x, height.toFloat(), line)
        }
        for (r in 1 until grid.rows) {
            val y = height.toFloat() * r / grid.rows
            canvas.drawLine(0f, y, width.toFloat(), y, line)
        }
        if (grid.labels) {
            for (r in 0 until grid.rows) {
                for (c in 0 until grid.columns) {
                    val cx = width.toFloat() * (c + 0.5f) / grid.columns
                    val cy = height.toFloat() * (r + 0.5f) / grid.rows
                    val label = "${('A' + c)}${r + 1}"
                    canvas.drawText(label, cx, cy, text)
                }
            }
        }
    }
}
