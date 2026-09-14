@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.painterly.app.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import com.painterly.app.ui.PaintingUiState
import kotlin.math.roundToInt

/** Artistic-language adjustments for the currently selected stage. */
@Composable
fun AdjustmentsPanel(
    state: PaintingUiState,
    onParamsChange: (StageParameters) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = state.params
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Stage ${state.currentStage} · ${LayerId.stageTitle(state.currentStage)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            LayerId.stagePurpose(state.currentStage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        HorizontalDivider()
        Column(Modifier.padding(top = 12.dp)) {
            when (state.currentStage) {
                1 -> {
                    ArtisticSlider("Detail", "Simpler", "More detail", params.drawingDetail) {
                        onParamsChange(params.copy(drawingDetail = it))
                    }
                    ArtisticSlider("Line strength", "Faint", "Bold", params.drawingSensitivity) {
                        onParamsChange(params.copy(drawingSensitivity = it))
                    }
                    ArtisticSlider("Shape simplification", "Keep everything", "Only the big shapes", params.drawingSimplification) {
                        onParamsChange(params.copy(drawingSimplification = it))
                    }
                }
                2 -> {
                    ValueCountSelector(params.blockInValues) {
                        onParamsChange(params.copy(blockInValues = it))
                    }
                    ArtisticSlider("Simplification", "More shapes", "Fewer, bigger masses", params.blockInSimplification) {
                        onParamsChange(params.copy(blockInSimplification = it))
                    }
                }
                3 -> ArtisticSlider("Shadow amount", "Only the darkest", "More of the darks", params.shadowAmount) {
                    onParamsChange(params.copy(shadowAmount = it))
                }
                4 -> ArtisticSlider("Light amount", "Only the brightest", "More of the lights", params.lightAmount) {
                    onParamsChange(params.copy(lightAmount = it))
                }
                5 -> ArtisticSlider("Form transitions", "Fewer turns", "More turns", params.coreAmount) {
                    onParamsChange(params.copy(coreAmount = it))
                }
                6 -> {
                    ArtisticSlider("Accent strength", "Subtle", "Bold", params.accentStrength) {
                        onParamsChange(params.copy(accentStrength = it))
                    }
                    CountSlider("Number of accents", params.accentCount, 4, 40) {
                        onParamsChange(params.copy(accentCount = it))
                    }
                }
                7 -> {
                    val refinement = params.refinement
                    ArtisticSlider("Edge awareness", "Loose", "Attentive", refinement.edgeControl) {
                        onParamsChange(params.copy(refinement = refinement.copy(edgeControl = it)))
                    }
                    ArtisticSlider("Detail priority", "Simplify", "Refine", refinement.detailPriority) {
                        onParamsChange(params.copy(refinement = refinement.copy(detailPriority = it)))
                    }
                    ArtisticSlider("Texture", "Smooth", "Textured", refinement.texture) {
                        onParamsChange(params.copy(refinement = refinement.copy(texture = it)))
                    }
                    ArtisticSlider("Colour variation", "Flat", "Shifting", refinement.colourVariation) {
                        onParamsChange(params.copy(refinement = refinement.copy(colourVariation = it)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCountSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Value masses", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == 3,
                onClick = { onSelect(3) },
                label = { Text("3 values") },
            )
            FilterChip(
                selected = selected == 4,
                onClick = { onSelect(4) },
                label = { Text("4 values") },
            )
        }
    }
}

@Composable
private fun ArtisticSlider(
    title: String,
    startLabel: String,
    endLabel: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Slider(value = value, onValueChange = onChange)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountSlider(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("$value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
        )
    }
}

/** Per-layer toggles and opacity, grouped by stage. */
@Composable
fun LayersPanel(
    state: PaintingUiState,
    onToggle: (LayerId, Boolean) -> Unit,
    onOpacity: (LayerId, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        LayerId.stageNumbers.forEach { stage ->
            Text(
                "Stage $stage · ${LayerId.stageTitle(stage)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            LayerId.forStage(stage).forEach { layer ->
                val enabled = state.enabledLayers[layer] == true
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(layer.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            layer.blurb,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { onToggle(layer, it) })
                }
                if (enabled) {
                    Slider(
                        value = state.layerOpacity[layer] ?: 1f,
                        onValueChange = { onOpacity(layer, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }
}
