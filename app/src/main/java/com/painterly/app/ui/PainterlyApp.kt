package com.painterly.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.painterly.app.model.LayerId
import com.painterly.app.ui.controls.AdjustmentsPanel
import com.painterly.app.ui.controls.ExportDialog
import com.painterly.app.ui.controls.LayersPanel
import com.painterly.app.ui.controls.StageNavigation
import com.painterly.app.ui.viewer.ViewerArea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PainterlyApp(
    viewModel: PainterlyViewModel,
    windowSizeClass: WindowSizeClass,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAdjust by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }
    val launchPicker: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeTransientMessage()
        }
    }
    LaunchedEffect(state.info) {
        val message = state.info
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeTransientMessage()
        }
    }

    val expanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val medium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Painterly")
                        if (state.imageLoaded) {
                            Text(
                                state.imageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = launchPicker) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import reference")
                    }
                    IconButton(onClick = { showExport = true }, enabled = state.imageLoaded) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                },
            )
        },
        bottomBar = {
            if (!expanded && !medium && state.imageLoaded) {
                NavigationBar {
                    NavigationBarItem(
                        selected = false,
                        onClick = launchPicker,
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        label = { Text("Reference") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showAdjust = true },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        label = { Text("Adjust") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showLayers = true },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                        label = { Text("Layers") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = viewModel::cycleCompareMode,
                        icon = { Icon(Icons.Default.Compare, contentDescription = null) },
                        label = { Text("Compare") },
                    )
                }
            }
        },
    ) { insets ->
        Box(
            Modifier
                .padding(insets)
                .fillMaxSize(),
        ) {
            when {
                !state.imageLoaded -> LandingScreen(
                    isProcessing = state.isProcessing,
                    progress = state.progress,
                    status = state.statusText,
                    onChoose = launchPicker,
                )
                expanded -> ExpandedLayout(state, viewModel)
                medium -> MediumLayout(state, viewModel)
                else -> CompactLayout(state, viewModel)
            }

            if (state.imageLoaded && (state.isProcessing || state.exportInProgress)) {
                ProcessingBanner(
                    progress = state.progress,
                    status = state.statusText,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showAdjust) {
        ModalBottomSheet(onDismissRequest = { showAdjust = false }) {
            AdjustmentsPanel(state = state, onParamsChange = viewModel::updateParameters)
        }
    }
    if (showLayers) {
        ModalBottomSheet(onDismissRequest = { showLayers = false }) {
            LayersPanel(
                state = state,
                onToggle = viewModel::toggleLayer,
                onOpacity = viewModel::setLayerOpacity,
            )
        }
    }
    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onExportCurrent = {
                viewModel.exportCurrent(it)
                showExport = false
            },
            onExportStages = {
                viewModel.exportStages(it)
                showExport = false
            },
        )
    }
}

@Composable
private fun CompactLayout(state: PaintingUiState, viewModel: PainterlyViewModel) {
    Column(Modifier.fillMaxSize()) {
        ViewerArea(
            state = state,
            onFocus = viewModel::setFocus,
            onCycleCompare = viewModel::cycleCompareMode,
            onCompareOpacity = viewModel::setCompareOpacity,
            onToggleReference = viewModel::toggleReference,
            modifier = Modifier.weight(1f),
        )
        StageNavigation(
            currentStage = state.currentStage,
            onSelect = viewModel::selectStage,
            vertical = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MediumLayout(state: PaintingUiState, viewModel: PainterlyViewModel) {
    Row(Modifier.fillMaxSize()) {
        StageNavigation(
            currentStage = state.currentStage,
            onSelect = viewModel::selectStage,
            vertical = true,
            modifier = Modifier
                .width(184.dp)
                .fillMaxHeight(),
        )
        VerticalDivider()
        Column(Modifier.weight(1f)) {
            ViewerArea(
                state = state,
                onFocus = viewModel::setFocus,
                onCycleCompare = viewModel::cycleCompareMode,
                onCompareOpacity = viewModel::setCompareOpacity,
            onToggleReference = viewModel::toggleReference,
                modifier = Modifier.weight(1f),
            )
            StageNavigation(
                currentStage = state.currentStage,
                onSelect = viewModel::selectStage,
                vertical = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExpandedLayout(state: PaintingUiState, viewModel: PainterlyViewModel) {
    Row(Modifier.fillMaxSize()) {
        StageNavigation(
            currentStage = state.currentStage,
            onSelect = viewModel::selectStage,
            vertical = true,
            modifier = Modifier
                .width(196.dp)
                .fillMaxHeight(),
        )
        VerticalDivider()
        ViewerArea(
            state = state,
            onFocus = viewModel::setFocus,
            onCycleCompare = viewModel::cycleCompareMode,
            onCompareOpacity = viewModel::setCompareOpacity,
            onToggleReference = viewModel::toggleReference,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider()
        InspectorPanel(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .width(336.dp)
                .fillMaxHeight(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorPanel(
    state: PaintingUiState,
    viewModel: PainterlyViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Adjust") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Layers") },
            )
        }
        if (tab == 0) {
            AdjustmentsPanel(
                state = state,
                onParamsChange = viewModel::updateParameters,
                modifier = Modifier.weight(1f),
            )
        } else {
            LayersPanel(
                state = state,
                onToggle = viewModel::toggleLayer,
                onOpacity = viewModel::setLayerOpacity,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LandingScreen(
    isProcessing: Boolean,
    progress: Float,
    status: String,
    onChoose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Turn a reference into a painting plan",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Painterly studies your reference on-device and breaks it into seven progressive stages — from the first drawing to the final refinement. Nothing is uploaded, and it works completely offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = onChoose,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("Choose a reference photo")
        }
        if (isProcessing) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
            Text(
                status.ifBlank { "Analysing…" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LayerId.stageNumbers.forEach { stage ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "$stage · ${LayerId.stageTitle(stage)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            LayerId.stagePurpose(stage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingBanner(progress: Float, status: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                status.ifBlank { "Analysing…" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
