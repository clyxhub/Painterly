package com.painterly.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Memory-safe decoding: bounds-first sampling, EXIF orientation correction and a hard
 * cap on the working resolution. Aspect ratio is always preserved.
 */
object BitmapLoader {

    fun decodeNormalized(context: Context, uri: Uri, maxDim: Int): Bitmap? =
        decodeNormalized({ context.contentResolver.openInputStream(uri) }, maxDim)

    fun decodeNormalized(file: File, maxDim: Int): Bitmap? =
        decodeNormalized({ if (file.exists()) file.inputStream() else null }, maxDim)

    private fun decodeNormalized(open: () -> InputStream?, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            open()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: Exception) {
            null
        } ?: return null

        val oriented = applyOrientation(decoded, readOrientation(open))
        val scaled = scaleToFit(oriented, maxDim)
        if (oriented !== decoded) decoded.recycle()
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    private fun readOrientation(open: () -> InputStream?): Int = try {
        open()?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
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
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    fun scaleToFit(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxDim) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
