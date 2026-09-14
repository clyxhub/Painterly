package com.painterly.app.ui.workspace

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.painterly.app.model.PaintingStage
import com.painterly.app.model.RefineView
import com.painterly.app.model.StageSettings
import com.painterly.app.model.ViewMode
import com.painterly.app.ui.components.PaintingCanvas
import com.painterly.app.ui.components.ProgressBar
import com.painterly.app.ui.components.SegmentedControl
import com.painterly.app.ui.components.StageSlider
import com.painterly.app.ui.components.StageStrip
import com.painterly.app.ui.components.StepperRow
import com.painterly.app.ui.components.ValueCountToggle
import com.painterly.app.ui.components.rememberCanvasTransform
import kotlinx.coroutines.delay

private val PaperBackground = Color(0xFFF4EEE3)
private val ExpandedWidth = 720.dp

@Composable
fun WorkspaceScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = viewModel(),
) {
    LaunchedEffect(projectId) { viewModel.start(projectId) }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val reference by viewModel.reference.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val compareOpacity by viewModel.compareOpacity.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val transform = rememberCanvasTransform()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRename by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingExport?.invoke()
        pendingExport = null
    }

    fun runExport(exportAll: Boolean) {
        val action = { if (exportAll) viewModel.exportAllStages() else viewModel.exportCurrent() }
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingExport = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            viewModel.dismissMessage()
        }
    }

    val activeLayers: List<Bitmap> = when (view) {
        ReferenceView.REFERENCE -> emptyList()
        ReferenceView.GUIDE, ReferenceView.COMPARE -> when (mode) {
            ViewMode.ADD -> listOfNotNull(layers[stage])
            ViewMode.COMPLETE -> PaintingStage.entries
                .filter { it.index <= stage.index }
                .mapNotNull { layers[it] }
        }
    }
    val showReference = view != ReferenceView.GUIDE
    val layerAlpha = if (view == ReferenceView.COMPARE) compareOpacity else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= ExpandedWidth
            if (isExpanded) {
                TabletWorkspace(
                    title = project?.name ?: "Painting",
                    reference = reference,
                    layers = activeLayers,
                    showReference = showReference,
                    layerAlpha = layerAlpha,
                    grid = grid,
                    transform = transform,
                    view = view,
                    progress = progress,
                    working = working,
                    onBack = onBack,
                    viewModel = viewModel,
                    onExportCurrent = { runExport(false) },
                    onExportAll = { runExport(true) },
                    onRename = { showRename = true },
                )
            } else {
                PhoneWorkspace(
                    title = project?.name ?: "Painting",
                    reference = reference,
                    layers = activeLayers,
                    showReference = showReference,
                    layerAlpha = layerAlpha,
                    grid = grid,
                    transform = transform,
                    view = view,
                    progress = progress,
                    working = working,
                    onBack = onBack,
                    viewModel = viewModel,
                    onExportCurrent = { runExport(false) },
                    onExportAll = { runExport(true) },
                    onRename = { showRename = true },
                )
            }
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Building your painting guide…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        message?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }

    if (showRename) {
        RenameDialog(
            current = project?.name ?: "",
            onDismiss = { showRename = false },
            onConfirm = {
                viewModel.rename(it)
                showRename = false
            },
        )
    }
}

@Composable
private fun PhoneWorkspace(
    title: String,
    reference: Bitmap?,
    layers: List<Bitmap>,
    showReference: Boolean,
    layerAlpha: Float,
    grid: com.painterly.app.model.GridSettings,
    transform: com.painterly.app.ui.components.CanvasTransform,
    view: ReferenceView,
    progress: Float,
    working: Boolean,
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel,
    onExportCurrent: () -> Unit,
    onExportAll: () -> Unit,
    onRename: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxSize()) {
        WorkspaceTopBar(
            title = title,
            onBack = onBack,
            onExportCurrent = onExportCurrent,
            onExportAll = onExportAll,
            onRename = onRename,
            onDelete = null,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            CanvasStage(
                reference = reference,
                layers = layers,
                showReference = showReference,
                layerAlpha = layerAlpha,
                grid = grid,
                transform = transform,
                view = view,
                progress = progress,
                working = working,
                viewModel = viewModel,
            )
        }
        PhoneControls(
            viewModel = viewModel,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
    }
}

@Composable
private fun TabletWorkspace(
    title: String,
    reference: Bitmap?,
    layers: List<Bitmap>,
    showReference: Boolean,
    layerAlpha: Float,
    grid: com.painterly.app.model.GridSettings,
    transform: com.painterly.app.ui.components.CanvasTransform,
    view: ReferenceView,
    progress: Float,
    working: Boolean,
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel,
    onExportCurrent: () -> Unit,
    onExportAll: () -> Unit,
    onRename: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WorkspaceTopBar(
            title = title,
            onBack = onBack,
            onExportCurrent = onExportCurrent,
            onExportAll = onExportAll,
            onRename = onRename,
            onDelete = null,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            StageRail(viewModel = viewModel, modifier = Modifier.width(132.dp).fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                CanvasStage(
                    reference = reference,
                    layers = layers,
                    showReference = showReference,
                    layerAlpha = layerAlpha,
                    grid = grid,
                    transform = transform,
                    view = view,
                    progress = progress,
                    working = working,
                    viewModel = viewModel,
                )
            }
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ControlsBody(viewModel)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    title: String,
    onBack: () -> Unit,
    onExportCurrent: () -> Unit,
    onExportAll: () -> Unit,
    onRename: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Export this view") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExportCurrent()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export all stage guides") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExportAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasStage(
    reference: Bitmap?,
    layers: List<Bitmap>,
    showReference: Boolean,
    layerAlpha: Float,
    grid: com.painterly.app.model.GridSettings,
    transform: com.painterly.app.ui.components.CanvasTransform,
    view: ReferenceView,
    progress: Float,
    working: Boolean,
    viewModel: WorkspaceViewModel,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PaintingCanvas(
            reference = reference,
            layers = layers,
            showReference = showReference,
            layerAlpha = layerAlpha,
            background = PaperBackground,
            grid = grid,
            transform = transform,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedControl(
                options = ReferenceView.entries,
                selected = view,
                label = { it.label },
                onSelect = viewModel::setView,
            )
            Row {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Row {
                        IconButton(onClick = { transform.reset() }) {
                            Icon(
                                Icons.Filled.CenterFocusStrong,
                                contentDescription = "Fit to screen",
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.updateGrid { it.copy(enabled = !it.enabled) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.GridOn,
                                contentDescription = "Toggle grid",
                                tint = if (grid.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }

        if (working) {
            ProgressBar(
                value = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PhoneControls(
    viewModel: WorkspaceViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stage.label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stage.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) "Collapse controls" else "Expand controls",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    ControlsBody(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ControlsBody(viewModel: WorkspaceViewModel) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val compareOpacity by viewModel.compareOpacity.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (stage != PaintingStage.DRAW) {
            SegmentedControl(
                options = ViewMode.entries,
                selected = mode,
                label = { it.label },
                onSelect = viewModel::setMode,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StageStrip(
            current = stage,
            onSelect = viewModel::selectStage,
        )
        StageSpecificControls(
            stage = stage,
            settings = settings,
            viewModel = viewModel,
        )
        if (view == ReferenceView.COMPARE) {
            StageSlider(
                title = "Compare with the reference",
                leftLabel = "Reference",
                rightLabel = "Guide",
                value = compareOpacity,
                onValueChange = viewModel::setCompareOpacity,
            )
        }
        if (grid.enabled) {
            HorizontalDivider()
            GridControls(viewModel = viewModel)
        }
    }
}

@Composable
private fun StageSpecificControls(
    stage: PaintingStage,
    settings: StageSettings,
    viewModel: WorkspaceViewModel,
) {
    when (stage) {
        PaintingStage.DRAW -> StageSlider(
            title = "Amount of structure",
            leftLabel = "Simpler",
            rightLabel = "More detailed",
            value = settings.drawDetail,
            onValueChange = viewModel::setDrawDetail,
        )

        PaintingStage.BLOCK_IN -> ValueCountToggle(
            count = settings.blockInValues,
            onChange = viewModel::setBlockInValues,
        )

        PaintingStage.SHADOWS -> StageSlider(
            title = "Shadow masses",
            leftLabel = "Less shadow",
            rightLabel = "More shadow",
            value = settings.shadowAmount,
            onValueChange = viewModel::setShadowAmount,
        )

        PaintingStage.LIGHTS -> StageSlider(
            title = "Light shapes",
            leftLabel = "Simpler",
            rightLabel = "More complete",
            value = settings.lightAmount,
            onValueChange = viewModel::setLightAmount,
        )

        PaintingStage.FORM -> StageSlider(
            title = "Turning form",
            leftLabel = "Less form",
            rightLabel = "More form",
            value = settings.formAmount,
            onValueChange = viewModel::setFormAmount,
        )

        PaintingStage.ACCENTS -> StageSlider(
            title = "Accents",
            leftLabel = "Fewer accents",
            rightLabel = "More accents",
            value = settings.accentAmount,
            onValueChange = viewModel::setAccentAmount,
        )

        PaintingStage.REFINE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(
                options = RefineView.entries,
                selected = settings.refineView,
                label = { it.label },
                onSelect = viewModel::setRefineView,
            )
            StageSlider(
                title = if (settings.refineView == RefineView.EDGES) {
                    "Edge emphasis"
                } else {
                    "Detail priority"
                },
                leftLabel = "Fewer notes",
                rightLabel = "More notes",
                value = settings.refineAmount,
                onValueChange = viewModel::setRefineAmount,
            )
        }
    }
}

@Composable
private fun GridControls(viewModel: WorkspaceViewModel) {
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Drawing grid",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StepperRow(
            label = "Rows",
            value = grid.rows,
            min = 1,
            max = 12,
            onValueChange = { value ->
                viewModel.updateGrid { it.copy(rows = value) }
            },
        )
        StepperRow(
            label = "Columns",
            value = grid.columns,
            min = 1,
            max = 12,
            onValueChange = { value ->
                viewModel.updateGrid { it.copy(columns = value) }
            },
        )
        StageSlider(
            title = "Grid strength",
            leftLabel = "Faint",
            rightLabel = "Strong",
            value = grid.opacity,
            onValueChange = { value ->
                viewModel.updateGrid { it.copy(opacity = value) }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show cell labels",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = grid.labels,
                onCheckedChange = { checked ->
                    viewModel.updateGrid { it.copy(labels = checked) }
                },
            )
        }
    }
}

@Composable
private fun StageRail(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            PaintingStage.entries.forEach { entry ->
                val selected = entry == stage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectStage(entry) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${entry.index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename painting") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Painting name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
