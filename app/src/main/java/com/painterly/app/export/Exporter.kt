package com.painterly.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.painterly.app.image.ImageLoader
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import com.painterly.app.processing.StageGenerators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Full-resolution export.
 *
 * Layers are regenerated at the requested resolution and composited one at a
 * time, so peak memory is only the source + canvas + the layer currently being
 * drawn. Exports preserve the reference aspect ratio because the canvas matches
 * the decoded source dimensions exactly.
 */
class Exporter(private val context: Context) {

    class Result(val locations: List<String>)

    suspend fun exportComposition(
        uri: Uri,
        params: StageParameters,
        orderedLayers: List<LayerId>,
        opacityOf: (LayerId) -> Float,
        baseColor: Int,
        maxDimension: Int,
        fileName: String,
    ): Result = withContext(Dispatchers.Default) {
        val source = decode(uri, maxDimension)
        val canvas = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            drawLayers(canvas, pixels, source.width, source.height, params, orderedLayers, opacityOf, baseColor)
            Result(listOf(save(canvas, fileName)))
        } finally {
            canvas.recycle()
            source.recycle()
        }
    }

    suspend fun exportStageSequence(
        uri: Uri,
        params: StageParameters,
        baseColor: Int,
        maxDimension: Int,
        namePrefix: String,
        onProgress: (Float, String) -> Unit,
    ): Result = withContext(Dispatchers.Default) {
        val source = decode(uri, maxDimension)
        val canvas = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val locations = ArrayList<String>()
        try {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

            val draw = Canvas(canvas)
            draw.drawColor(baseColor)

            for (stage in 1..LayerId.LAST_STAGE) {
                val layers = LayerId.forStage(stage)
                for (layer in layers) {
                    drawLayer(draw, pixels, source.width, source.height, params, layer, 1f)
                }
                val fileName = "${namePrefix}_stage_${stage}_${LayerId.stageTitle(stage).lowercase().replace(' ', '_')}.png"
                locations += save(canvas, fileName)
                onProgress(stage / LayerId.LAST_STAGE.toFloat(), LayerId.stageTitle(stage))
            }
            Result(locations)
        } finally {
            canvas.recycle()
            source.recycle()
        }
    }

    private fun decode(uri: Uri, maxDimension: Int): Bitmap =
        ImageLoader.decode(context, uri, maxDimension)
            ?: throw IOException("The reference image could not be decoded for export")

    private fun drawLayers(
        canvas: Bitmap,
        pixels: IntArray,
        width: Int,
        height: Int,
        params: StageParameters,
        orderedLayers: List<LayerId>,
        opacityOf: (LayerId) -> Float,
        baseColor: Int,
    ) {
        val draw = Canvas(canvas)
        draw.drawColor(baseColor)
        for (layer in orderedLayers) {
            drawLayer(draw, pixels, width, height, params, layer, opacityOf(layer))
        }
    }

    private fun drawLayer(
        canvas: Canvas,
        pixels: IntArray,
        width: Int,
        height: Int,
        params: StageParameters,
        layer: LayerId,
        opacity: Float,
    ) {
        val layerPixels = StageGenerators.generate(layer, pixels, width, height, params)
        val bitmap = Bitmap.createBitmap(layerPixels, width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            isFilterBitmap = true
            alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
    }

    private fun save(bitmap: Bitmap, fileName: String): String {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Painterly",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create an export file")
            try {
                resolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: throw IOException("Could not open the export file for writing")
            } finally {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            "Pictures/Painterly/$fileName"
        } else {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Painterly",
            ).apply { mkdirs() }
            val file = File(directory, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }
    }
}
