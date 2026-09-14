package com.painterly.app.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.painterly.app.model.LayerId
import com.painterly.app.ui.CompareMode
import com.painterly.app.ui.PaintingUiState
import com.painterly.app.ui.ViewFocus
import kotlin.math.roundToInt

/** The artwork area: stage rendering, zoom/pan and reference comparison. */
@Composable
fun ViewerArea(
    state: PaintingUiState,
    onFocus: (ViewFocus) -> Unit,
    onCycleCompare: () -> Unit,
    onCompareOpacity: (Float) -> Unit,
    onToggleReference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val painting = state.preview
        if (painting == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Preparing the canvas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ComparisonContent(
                reference = state.source,
                painting = painting,
                mode = state.compareMode,
                showReference = state.showReference,
                compareOpacity = state.compareOpacity,
                modifier = Modifier.fillMaxSize(),
            )
        }

        StageChip(state, Modifier.align(Alignment.TopStart).padding(12.dp))
        FocusPills(
            selected = state.focus,
            onFocus = onFocus,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
        CompareBar(
            mode = state.compareMode,
            opacity = state.compareOpacity,
            enabled = painting != null,
            onCycle = onCycleCompare,
            onOpacity = onCompareOpacity,
            onToggleReference = onToggleReference,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

@Composable
private fun StageChip(state: PaintingUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 150.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
    ) {
        Text(
            "Stage ${state.currentStage} · ${LayerId.stageTitle(state.currentStage)}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FocusPills(
    selected: ViewFocus,
    onFocus: (ViewFocus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        PillToggle("This step", selected == ViewFocus.NEW_INFO) { onFocus(ViewFocus.NEW_INFO) }
        Box(Modifier.padding(top = 6.dp)) {
            PillToggle("Painting so far", selected == ViewFocus.FULL_COMPOSITION) {
                onFocus(ViewFocus.FULL_COMPOSITION)
            }
        }
    }
}

@Composable
private fun PillToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        },
        tonalElevation = if (selected) 0.dp else 4.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun CompareBar(
    mode: CompareMode,
    opacity: Float,
    enabled: Boolean,
    onCycle: () -> Unit,
    onOpacity: (Float) -> Unit,
    onToggleReference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled || mode == CompareMode.OFF) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Comparing: ${compareLabel(mode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == CompareMode.TOGGLE) {
                        PillToggle("Swap", false, onToggleReference)
                    }
                    PillToggle("Change", false, onCycle)
                }
            }
            if (mode == CompareMode.OPACITY) {
                Slider(value = opacity, onValueChange = onOpacity)
            }
        }
    }
}

private fun compareLabel(mode: CompareMode): String = when (mode) {
    CompareMode.OFF -> "Off"
    CompareMode.SIDE_BY_SIDE -> "Side by side"
    CompareMode.TOGGLE -> "Swap view"
    CompareMode.OPACITY -> "Blend"
    CompareMode.SWIPE -> "Swipe"
}

@Composable
private fun ComparisonContent(
    reference: Bitmap?,
    painting: Bitmap,
    mode: CompareMode,
    showReference: Boolean,
    compareOpacity: Float,
    modifier: Modifier,
) {
    ZoomableBox(modifier) {
        when (mode) {
            CompareMode.OFF -> ImagePane(painting)
            CompareMode.TOGGLE -> {
                val target = if (showReference && reference != null) reference else painting
                ImagePane(target)
            }
            CompareMode.OPACITY -> {
                Box(Modifier.fillMaxSize()) {
                    if (reference != null) ImagePane(reference)
                    ImagePane(painting, alpha = compareOpacity)
                }
            }
            CompareMode.SIDE_BY_SIDE -> {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (reference != null) ImagePane(reference)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        ImagePane(painting)
                    }
                }
            }
            CompareMode.SWIPE -> {
                if (reference != null) SwipeCompare(reference, painting)
            }
        }
    }
}

@Composable
private fun ImagePane(bitmap: Bitmap, alpha: Float = 1f) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun SwipeCompare(reference: Bitmap, painting: Bitmap) {
    var fraction by remember { mutableStateOf(0.5f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        fraction = (fraction + dragAmount / widthPx).coerceIn(0.05f, 0.95f)
                    }
                },
        ) {
            ImagePane(reference)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(left = 0f, top = 0f, right = size.width * fraction, bottom = size.height) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                ImagePane(painting)
            }
            Box(
                Modifier
                    .offset { IntOffset((widthPx * fraction).roundToInt(), 0) }
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ZoomableBox(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 8f)
        if (nextScale <= 1.001f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val maxX = (nextScale - 1f) * containerSize.width / 2f
            val maxY = (nextScale - 1f) * containerSize.height / 2f
            scale = nextScale
            offset = Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
            content = content,
        )
    }
}
