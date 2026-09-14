package com.painterly.app.ui.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ExportResolution(val label: String, val pixels: Int)

private val resolutions = listOf(
    ExportResolution("Preview · 1280 px", 1280),
    ExportResolution("Standard · 2048 px", 2048),
    ExportResolution("High · 3072 px", 3072),
)

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportCurrent: (Int) -> Unit,
    onExportStages: (Int) -> Unit,
) {
    var selected by remember { mutableStateOf(resolutions[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export painting") },
        text = {
            Column {
                Text(
                    "Longest edge of the exported file. Aspect ratio is always preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                resolutions.forEach { resolution ->
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == resolution,
                                onClick = { selected = resolution },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == resolution,
                            onClick = { selected = resolution },
                        )
                        Text(resolution.label, Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(
                    onClick = { onExportStages(selected.pixels) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("Export all 7 stages")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExportCurrent(selected.pixels) }) {
                Text("Current view")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
