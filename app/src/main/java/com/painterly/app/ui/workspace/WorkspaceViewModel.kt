package com.painterly.app.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.painterly.app.data.ProjectRepository
import com.painterly.app.export.Exporter
import com.painterly.app.imaging.SharedIntermediates
import com.painterly.app.imaging.StageGenerator
import com.painterly.app.model.GridSettings
import com.painterly.app.model.PaintingStage
import com.painterly.app.model.Project
import com.painterly.app.model.RefineView
import com.painterly.app.model.StageSettings
import com.painterly.app.model.ViewMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ReferenceView(val label: String) {
    REFERENCE("Reference"),
    GUIDE("Guide"),
    COMPARE("Compare"),
}

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository.get(application)

    private var projectId: String = ""
    private var currentSettings: StageSettings = StageSettings()

    @Volatile
    private var shared: SharedIntermediates? = null

    private var generationJob: Job? = null
    private var loaded = false

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _reference = MutableStateFlow<Bitmap?>(null)
    val reference: StateFlow<Bitmap?> = _reference.asStateFlow()

    private val _layers = MutableStateFlow<Map<PaintingStage, Bitmap>>(emptyMap())
    val layers: StateFlow<Map<PaintingStage, Bitmap>> = _layers.asStateFlow()

    private val _settings = MutableStateFlow(StageSettings())
    val settings: StateFlow<StageSettings> = _settings.asStateFlow()

    private val _stage = MutableStateFlow(PaintingStage.DRAW)
    val stage: StateFlow<PaintingStage> = _stage.asStateFlow()

    private val _mode = MutableStateFlow(ViewMode.ADD)
    val mode: StateFlow<ViewMode> = _mode.asStateFlow()

    private val _view = MutableStateFlow(ReferenceView.GUIDE)
    val view: StateFlow<ReferenceView> = _view.asStateFlow()

    private val _compareOpacity = MutableStateFlow(0.6f)
    val compareOpacity: StateFlow<Float> = _compareOpacity.asStateFlow()

    private val _grid = MutableStateFlow(GridSettings())
    val grid: StateFlow<GridSettings> = _grid.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _working = MutableStateFlow(true)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun start(projectId: String) {
        if (loaded) return
        loaded = true
        this.projectId = projectId
        viewModelScope.launch { loadProject(projectId) }
    }

    private suspend fun loadProject(projectId: String) {
        try {
            val project = withContext(Dispatchers.IO) { repository.loadProject(projectId) }
                ?: throw IOException("Project not found")
            currentSettings = project.settings
            _settings.value = project.settings
            _stage.value = project.lastStage
            _project.value = project

            val reference = withContext(Dispatchers.IO) { repository.loadReference(project) }
                ?: throw IOException("Reference image is missing")

            val cachedSignature = withContext(Dispatchers.IO) { repository.cacheSignature(projectId) }
            val cached = if (cachedSignature == project.settings.fullSignature()) {
                withContext(Dispatchers.IO) { repository.loadCachedLayers(projectId) }
            } else {
                null
            }

            if (cached != null && cached.keys.containsAll(PaintingStage.entries)) {
                _reference.value = reference
                _layers.value = cached
                _progress.value = 1f
            } else {
                _reference.value = reference
                val generated = buildAll(project.settings)
                _layers.value = generated
                withContext(Dispatchers.IO) {
                    repository.saveLayers(projectId, generated, project.settings.fullSignature())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _message.value = "We couldn't open this painting. ${e.message ?: ""}".trim()
        } finally {
            _loading.value = false
            _working.value = false
        }
    }

    private suspend fun buildAll(settings: StageSettings): Map<PaintingStage, Bitmap> {
        val reference = _reference.value ?: throw IOException("Reference image is missing")
        return withContext(Dispatchers.Default) {
            val localShared = StageGenerator.computeShared(reference, settings)
            shared = localShared
            val out = LinkedHashMap<PaintingStage, Bitmap>()
            PaintingStage.entries.forEachIndexed { index, stage ->
                ensureActive()
                out[stage] = StageGenerator.stage(stage, localShared, settings)
                _progress.value = (index + 1) / PaintingStage.entries.size.toFloat()
            }
            out
        }
    }

    // ------------------------------------------------------------------ navigation

    fun selectStage(stage: PaintingStage) {
        _stage.value = stage
        persistMeta()
    }

    fun setMode(mode: ViewMode) {
        _mode.value = mode
    }

    fun setView(view: ReferenceView) {
        _view.value = view
    }

    fun setCompareOpacity(value: Float) {
        _compareOpacity.value = value.coerceIn(0f, 1f)
    }

    fun updateGrid(update: (GridSettings) -> GridSettings) {
        _grid.value = update(_grid.value)
    }

    fun rename(name: String) {
        val project = _project.value ?: return
        val trimmed = name.trim().ifBlank { "Untitled Painting" }
        _project.value = project.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        persistMeta()
    }

    fun dismissMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------------ settings

    fun setDrawDetail(value: Float) = applySettings(currentSettings.copy(drawDetail = value))

    fun setBlockInValues(value: Int) =
        applySettings(currentSettings.copy(blockInValues = value.coerceIn(3, 4)))

    fun setShadowAmount(value: Float) = applySettings(currentSettings.copy(shadowAmount = value))

    fun setLightAmount(value: Float) = applySettings(currentSettings.copy(lightAmount = value))

    fun setFormAmount(value: Float) = applySettings(currentSettings.copy(formAmount = value))

    fun setAccentAmount(value: Float) = applySettings(currentSettings.copy(accentAmount = value))

    fun setRefineView(value: RefineView) = applySettings(currentSettings.copy(refineView = value))

    fun setRefineAmount(value: Float) = applySettings(currentSettings.copy(refineAmount = value))

    private fun applySettings(newSettings: StageSettings) {
        val old = currentSettings
        if (old == newSettings) return
        val changed = PaintingStage.entries
            .filter { old.dependencyKey(it) != newSettings.dependencyKey(it) }
            .toSet()
        currentSettings = newSettings
        _settings.value = newSettings
        persistMeta()
        if (changed.isNotEmpty()) regenerate(changed)
    }

    private fun regenerate(changedStages: Set<PaintingStage>) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                delay(140)
                val reference = _reference.value ?: return@launch
                val settingsSnapshot = currentSettings
                _working.value = true
                _progress.value = 0f

                val updated = withContext(Dispatchers.Default) {
                    val localShared = shared?.takeIf {
                        !changedStages.contains(PaintingStage.BLOCK_IN)
                    } ?: StageGenerator.computeShared(reference, settingsSnapshot).also { shared = it }

                    val result = LinkedHashMap(_layers.value)
                    changedStages.forEachIndexed { index, stage ->
                        ensureActive()
                        result[stage] = StageGenerator.stage(stage, localShared, settingsSnapshot)
                        _progress.value = (index + 1) / changedStages.size.toFloat()
                    }
                    result
                }

                _layers.value = updated
                withContext(Dispatchers.IO) {
                    repository.saveLayers(projectId, updated, settingsSnapshot.fullSignature())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _message.value = "Couldn't update this stage. Try again."
            } finally {
                _working.value = false
            }
        }
    }

    private fun persistMeta() {
        val project = _project.value ?: return
        val updated = project.copy(
            settings = currentSettings,
            lastStage = _stage.value,
            updatedAt = System.currentTimeMillis(),
        )
        _project.value = updated
        viewModelScope.launch(Dispatchers.IO) { repository.saveProject(updated) }
    }

    // ------------------------------------------------------------------ export

    fun visibleLayers(): List<Bitmap> {
        val map = _layers.value
        return when (_mode.value) {
            ViewMode.ADD -> listOfNotNull(map[_stage.value])
            ViewMode.COMPLETE -> PaintingStage.entries
                .filter { it.index <= _stage.value.index }
                .mapNotNull { map[it] }
        }
    }

    fun exportCurrent() {
        val reference = _reference.value ?: return
        val stage = _stage.value
        viewModelScope.launch {
            try {
                val layers = visibleLayers()
                val showReference = _view.value != ReferenceView.GUIDE
                val alpha = if (_view.value == ReferenceView.COMPARE) _compareOpacity.value else 1f
                val bitmap = withContext(Dispatchers.Default) {
                    Exporter.compose(
                        width = reference.width,
                        height = reference.height,
                        reference = if (showReference) reference else null,
                        layers = layers,
                        layerAlpha = alpha,
                        grid = _grid.value,
                        paperColor = Exporter.PAPER_ARGB,
                    )
                }
                val uri = withContext(Dispatchers.IO) {
                    Exporter.exportToPictures(
                        getApplication<Application>(),
                        bitmap,
                        Exporter.timestampedName(stage.name.lowercase()),
                    )
                }
                bitmap.recycle()
                _message.value = if (uri != null) "Saved to Pictures/Painterly" else "Export failed"
            } catch (e: Exception) {
                _message.value = "Export failed"
            }
        }
    }

    fun exportAllStages() {
        viewModelScope.launch {
            try {
                val map = _layers.value
                val reference = _reference.value ?: return@launch
                var saved = 0
                for (stage in PaintingStage.entries) {
                    val layers = PaintingStage.entries
                        .filter { it.index <= stage.index }
                        .mapNotNull { map[it] }
                    val bitmap = withContext(Dispatchers.Default) {
                        Exporter.compose(
                            width = reference.width,
                            height = reference.height,
                            reference = null,
                            layers = layers,
                            layerAlpha = 1f,
                            grid = _grid.value,
                            paperColor = Exporter.PAPER_ARGB,
                        )
                    }
                    val uri = withContext(Dispatchers.IO) {
                        Exporter.exportToPictures(
                            getApplication<Application>(),
                            bitmap,
                            Exporter.timestampedName("${stage.index + 1}_${stage.name.lowercase()}"),
                        )
                    }
                    bitmap.recycle()
                    if (uri != null) saved++
                }
                _message.value = if (saved > 0) "Saved $saved stage guides to Pictures/Painterly"
                else "Export failed"
            } catch (e: Exception) {
                _message.value = "Export failed"
            }
        }
    }
}
