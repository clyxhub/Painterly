package com.painterly.app.ui

import android.graphics.Bitmap
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters

/** "What to add next" vs "the painting as it stands after this stage". */
enum class ViewFocus { NEW_INFO, FULL_COMPOSITION }

/** How the reference is compared against the current painting. */
enum class CompareMode { OFF, SIDE_BY_SIDE, TOGGLE, OPACITY, SWIPE }

data class PaintingUiState(
    val imageLoaded: Boolean = false,
    val imageName: String = "",
    val source: Bitmap? = null,
    val preview: Bitmap? = null,
    val currentStage: Int = 1,
    val focus: ViewFocus = ViewFocus.FULL_COMPOSITION,
    val enabledLayers: Map<LayerId, Boolean> = defaultEnabledLayers(),
    val layerOpacity: Map<LayerId, Float> = defaultLayerOpacity(),
    val params: StageParameters = StageParameters(),
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null,
    val info: String? = null,
    val compareMode: CompareMode = CompareMode.OFF,
    val compareOpacity: Float = 0.5f,
    val showReference: Boolean = false,
    val exportInProgress: Boolean = false,
) {
    companion object {
        fun defaultEnabledLayers(): Map<LayerId, Boolean> =
            LayerId.entries.associateWith { it.defaultEnabled }

        fun defaultLayerOpacity(): Map<LayerId, Float> =
            LayerId.entries.associateWith { 1f }
    }
}
