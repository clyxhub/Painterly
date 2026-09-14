package com.painterly.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.painterly.app.model.GridSettings
import kotlin.math.max
import kotlin.math.min

/** Shared transform for every layer so they can never drift apart. */
@Stable
class CanvasTransform {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewport: Size = Size.Zero
    var content: Size = Size.Zero
    var maxScale: Float = 8f

    val isFit: Boolean get() = scale <= 1.001f && offset == Offset.Zero

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float) {
        if (viewport.width <= 0f || content.width <= 0f) return
        val newScale = (scale * zoom).coerceIn(1f, maxScale)
        val k = newScale / scale
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val moved = (centroid - center) * (1f - k) + offset * k + pan
        scale = newScale
        offset = moved
        clamp()
    }

    fun onDoubleTap(position: Offset) {
        if (viewport.width <= 0f || content.width <= 0f) return
        if (scale > 1.05f) {
            reset()
            return
        }
        val target = 2.6f
        val k = target / scale
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = (position - center) * (1f - k) + offset * k
        scale = target
        clamp()
    }

    fun clamp() {
        if (viewport.width <= 0f || content.width <= 0f) return
        val maxX = max(0f, (content.width * scale - viewport.width) / 2f)
        val maxY = max(0f, (content.height * scale - viewport.height) / 2f)
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

@Composable
fun rememberCanvasTransform(): CanvasTransform = remember { CanvasTransform() }

/**
 * Draws every layer into one transformed container. Because all layers share the container,
 * zoom/pan alignment is exact by construction.
 */
@Composable
fun PaintingCanvas(
    reference: android.graphics.Bitmap?,
    layers: List<android.graphics.Bitmap>,
    showReference: Boolean,
    layerAlpha: Float,
    background: Color,
    grid: GridSettings,
    transform: CanvasTransform,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val referenceImage = remember(reference) { reference?.asImageBitmap() }
    val layerImages = remember(layers) { layers.map { it.asImageBitmap() } }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.argb(220, 26, 24, 32)
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        if (viewport.width <= 0f || viewport.height <= 0f) return@BoxWithConstraints

        val imageW = (reference?.width ?: layers.firstOrNull()?.width ?: 1).toFloat()
        val imageH = (reference?.height ?: layers.firstOrNull()?.height ?: 1).toFloat()
        val aspect = (imageW / imageH).takeIf { it.isFinite() && it > 0f } ?: 1f

        val fitW: Float
        val fitH: Float
        if (viewport.width / viewport.height > aspect) {
            fitH = viewport.height
            fitW = fitH * aspect
        } else {
            fitW = viewport.width
            fitH = fitW / aspect
        }
        val contentSize = Size(fitW, fitH)

        LaunchedEffect(contentSize, viewport) {
            transform.viewport = viewport
            transform.content = contentSize
            transform.clamp()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(transform) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        transform.onTransform(centroid, pan, zoom)
                    }
                }
                .pointerInput(transform) {
                    detectTapGestures(onDoubleTap = { transform.onDoubleTap(it) })
                },
            contentAlignment = Alignment.Center,
        ) {
            val widthDp = with(density) { fitW.toDp() }
            val heightDp = with(density) { fitH.toDp() }
            Box(
                modifier = Modifier
                    .size(widthDp, heightDp)
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(background),
            ) {
                if (showReference && referenceImage != null) {
                    Image(
                        bitmap = referenceImage,
                        contentDescription = "Reference photograph",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
                layerImages.forEach { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        alpha = layerAlpha.coerceIn(0f, 1f),
                    )
                }
                if (grid.enabled) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val opacity = grid.opacity.coerceIn(0.05f, 1f)
                        val lineColor = Color(0.10f, 0.09f, 0.13f, opacity)
                        val stroke = (min(size.width, size.height) / 420f).coerceAtLeast(1f)
                        for (c in 1 until grid.columns) {
                            val x = size.width * c / grid.columns
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
                        }
                        for (r in 1 until grid.rows) {
                            val y = size.height * r / grid.rows
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                        }
                        if (grid.labels) {
                            val textSizePx =
                                (min(size.width, size.height) * 0.03f / transform.scale)
                                    .coerceAtLeast(1f)
                            labelPaint.textSize = textSizePx
                            labelPaint.alpha = (opacity * 255f).toInt().coerceIn(0, 255)
                            drawIntoCanvas { canvas ->
                                for (r in 0 until grid.rows) {
                                    for (c in 0 until grid.columns) {
                                        val cx = size.width * (c + 0.5f) / grid.columns
                                        val cy = size.height * (r + 0.5f) / grid.rows
                                        canvas.nativeCanvas.drawText(
                                            "${('A' + c)}${r + 1}",
                                            cx,
                                            cy + textSizePx * 0.35f,
                                            labelPaint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
