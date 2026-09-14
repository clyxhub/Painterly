package com.painterly.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

/**
 * Memory-safe image decoding.
 *
 * Callers never receive a bitmap larger than [params]. The decode uses a
 * power-of-two [BitmapFactory.Options.inSampleSize] first (cheap, avoids
 * allocating the full image) and then an exact downscale, so the device never
 * holds the original multi-megapixel buffer in memory.
 */
object ImageLoader {

    /** Decode [uri] so its longest edge is at most [maxDimension] pixels. */
    fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded: Bitmap = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }

        val scaled = scaleToMax(decoded, maxDimension)
        val oriented = applyOrientation(scaled, readOrientation(context, uri))
        return oriented
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMax(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    private fun readOrientation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            source
        }
    }
}
