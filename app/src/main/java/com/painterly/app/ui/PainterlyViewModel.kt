package com.painterly.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.painterly.app.export.Exporter
import com.painterly.app.image.ImageLoader
import com.painterly.app.model.Argb
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import com.painterly.app.processing.LayerCompositor
import com.painterly.app.processing.LayerRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class PainterlyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LayerRepository()
    private val exporter = Exporter(application)

    private val _ui = MutableStateFlow(PaintingUiState())
    val uiState: StateFlow<PaintingUiState> = _ui.asStateFlow()

    private var sourceUri: Uri? = null
    private var importJob: Job? = null
    private var paramsJob: Job? = null
    private var exportJob: Job? = null

    // ------------------------------------------------------------- importing

    fun importImage(uri: Uri) {
        importJob?.cancel()
        paramsJob?.cancel()
        sourceUri = uri
        _ui.update {
            it.copy(
                isProcessing = true,
                progress = 0f,
                statusText = "Opening reference",
                error = null,
                imageLoaded = false,
            )
        }
        importJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageLoader.decode(getApplication(), uri, PREVIEW_MAX_DIMENSION)
                } ?: throw IOException("That image could not be opened. Try a different photo.")

                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                repository.bindSource(pixels, width, height)

                _ui.update {
                    it.copy(
                        imageLoaded = true,
                        imageName = queryDisplayName(uri),
                        source = bitmap,
                        preview = null,
                        currentStage = 1,
                        focus = ViewFocus.FULL_COMPOSITION,
                        enabledLayers = PaintingUiState.defaultEnabledLayers(),
                        layerOpacity = PaintingUiState.defaultLayerOpacity(),
                        params = StageParameters(),
                        progress = 0f,
                        statusText = "Reading the reference",
                    )
                }
                generateLayers(null)
                publish()
                _ui.update { it.copy(isProcessing = false, progress = 1f, statusText = "") }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _ui.update { it.copy(isProcessing = false, statusText = "", error = friendly(t)) }
            }
        }
    }

    // --------------------------------------------------------------- viewing

    fun selectStage(stage: Int) {
        val target = stage.coerceIn(1, LayerId.LAST_STAGE)
        if (_ui.value.currentStage == target) return
        _ui.update { it.copy(currentStage = target, showReference = false) }
        viewModelScope.launch { publish() }
    }

    fun setFocus(focus: ViewFocus) {
        if (_ui.value.focus == focus) return
        _ui.update { it.copy(focus = focus) }
        viewModelScope.launch { publish() }
    }

    fun toggleLayer(layer: LayerId, enabled: Boolean) {
        _ui.update { it.copy(enabledLayers = it.enabledLayers + (layer to enabled)) }
        viewModelScope.launch { publish() }
    }

    fun setLayerOpacity(layer: LayerId, value: Float) {
        _ui.update { it.copy(layerOpacity = it.layerOpacity + (layer to value.coerceIn(0f, 1f))) }
        viewModelScope.launch { publish() }
    }

    fun setCompareMode(mode: CompareMode) {
        _ui.update { it.copy(compareMode = mode, showReference = false) }
    }

    fun cycleCompareMode() {
        val modes = CompareMode.entries
        val next = modes[(modes.indexOf(_ui.value.compareMode) + 1) % modes.size]
        setCompareMode(next)
    }

    fun setCompareOpacity(value: Float) {
        _ui.update { it.copy(compareOpacity = value.coerceIn(0f, 1f)) }
    }

    fun toggleReference() {
        _ui.update { it.copy(showReference = !it.showReference) }
    }

    // ------------------------------------------------------------- adjusting

    fun updateParameters(newParams: StageParameters) {
        val current = _ui.value.params
        if (newParams == current) return
        val affected = newParams.changedLayers(current)
        _ui.update { it.copy(params = newParams) }
        if (affected.isEmpty()) {
            viewModelScope.launch { publish() }
            return
        }
        paramsJob?.cancel()
        paramsJob = viewModelScope.launch {
            try {
                _ui.update { it.copy(isProcessing = true, progress = 0f, statusText = "Updating") }
                generateLayers(affected.toList())
                publish()
                _ui.update { it.copy(isProcessing = false, progress = 1f, statusText = "") }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _ui.update { it.copy(isProcessing = false, statusText = "", error = friendly(t)) }
            }
        }
    }

    // --------------------------------------------------------------- exporting

    fun exportCurrent(resolution: Int) {
        val state = _ui.value
        val uri = sourceUri
        if (uri == null || !state.imageLoaded) {
            _ui.update { it.copy(error = "Import a reference before exporting.") }
            return
        }
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                _ui.update { it.copy(exportInProgress = true, progress = 0f, statusText = "Exporting") }
                val spec = compositionSpec(state)
                val opacity = spec.toMap()
                val result = exporter.exportComposition(
                    uri = uri,
                    params = state.params,
                    orderedLayers = spec.map { it.first },
                    opacityOf = { opacity[it] ?: 1f },
                    baseColor = Argb.WHITE,
                    maxDimension = resolution,
                    fileName = "painterly_${System.currentTimeMillis()}.png",
                )
                _ui.update {
                    it.copy(
                        exportInProgress = false,
                        progress = 1f,
                        statusText = "",
                        info = "Saved to ${result.locations.first()}",
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _ui.update { it.copy(exportInProgress = false, statusText = "", error = friendly(t)) }
            }
        }
    }

    fun exportStages(resolution: Int) {
        val state = _ui.value
        val uri = sourceUri
        if (uri == null || !state.imageLoaded) {
            _ui.update { it.copy(error = "Import a reference before exporting.") }
            return
        }
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                _ui.update { it.copy(exportInProgress = true, progress = 0f, statusText = "Exporting") }
                val result = exporter.exportStageSequence(
                    uri = uri,
                    params = state.params,
                    baseColor = Argb.WHITE,
                    maxDimension = resolution,
                    namePrefix = "painterly_${System.currentTimeMillis()}",
                    onProgress = { value, label ->
                        _ui.update { it.copy(progress = value, statusText = "Saving $label") }
                    },
                )
                _ui.update {
                    it.copy(
                        exportInProgress = false,
                        progress = 1f,
                        statusText = "",
                        info = "Saved ${result.locations.size} stage images to Pictures/Painterly",
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _ui.update { it.copy(exportInProgress = false, statusText = "", error = friendly(t)) }
            }
        }
    }

    // ------------------------------------------------------------- internals

    private suspend fun generateLayers(only: List<LayerId>?) {
        val params = _ui.value.params
        val targets = only ?: LayerId.displayOrder
        targets.forEachIndexed { index, layer ->
            coroutineContext.ensureActive()
            if (only != null && repository.isCurrent(layer, params)) return@forEachIndexed
            _ui.update {
                it.copy(
                    progress = index.toFloat() / targets.size * 0.95f,
                    statusText = "Analysing ${layer.displayName}",
                )
            }
            repository.ensure(layer, params)
        }
    }

    private suspend fun publish() {
        val state = _ui.value
        if (!state.imageLoaded) return
        val composed = withContext(Dispatchers.Default) { buildComposition(state) } ?: return
        _ui.update { it.copy(preview = composed) }
    }

    private fun buildComposition(state: PaintingUiState): Bitmap? {
        val source = state.source ?: return null
        val layers = compositionSpec(state).mapNotNull { (layer, opacity) ->
            repository.peek(layer)?.let { it to opacity }
        }
        return LayerCompositor.compose(source.width, source.height, Argb.WHITE, layers)
    }

    /** Ordered layer/opacity list for the currently selected view. */
    private fun compositionSpec(state: PaintingUiState): List<Pair<LayerId, Float>> {
        val enabled = LayerId.displayOrder.filter { state.enabledLayers[it] == true }
        return when (state.focus) {
            ViewFocus.NEW_INFO -> {
                val context = enabled
                    .filter { it.stage < state.currentStage }
                    .map { it to CONTEXT_ALPHA }
                val fresh = enabled
                    .filter { it.stage == state.currentStage }
                    .map { it to (state.layerOpacity[it] ?: 1f) }
                context + fresh
            }
            ViewFocus.FULL_COMPOSITION -> enabled
                .filter { it.stage <= state.currentStage }
                .map { it to (state.layerOpacity[it] ?: 1f) }
        }
    }

    fun consumeTransientMessage() {
        _ui.update { it.copy(error = null, info = null) }
    }

    private fun queryDisplayName(uri: Uri): String = try {
        getApplication<Application>().contentResolver
            .query(uri, null, null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: "Reference"
    } catch (e: Exception) {
        "Reference"
    }

    private fun friendly(t: Throwable): String = when (t) {
        is OutOfMemoryError ->
            "This image is too large to process. Try a smaller photo."
        is SecurityException ->
            "Painterly does not have access to that image."
        is IOException ->
            t.message ?: "Something went wrong while reading that image."
        else ->
            t.message ?: "Something went wrong."
    }

    override fun onCleared() {
        super.onCleared()
        repository.clear()
    }

    companion object {
        const val PREVIEW_MAX_DIMENSION = 960
        private const val CONTEXT_ALPHA = 0.22f
    }
}
