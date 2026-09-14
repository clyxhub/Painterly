package com.painterly.app.model

/**
 * One paintable layer in the progressive stack. Multiple [LayerId]s can belong to
 * the same stage (stage 7 exposes several optional analysis overlays).
 *
 * The declaration order IS the paint order: earlier entries are laid down first,
 * later entries sit on top. [LayerId.entries] is therefore the canonical
 * bottom-to-top stacking order.
 */
enum class LayerId(
    val stage: Int,
    val displayName: String,
    val blurb: String,
    val defaultEnabled: Boolean,
) {
    DRAWING(
        stage = 1,
        displayName = "Drawing",
        blurb = "Silhouette and major shapes",
        defaultEnabled = true,
    ),
    BLOCK_IN(
        stage = 2,
        displayName = "Block-In",
        blurb = "Large paintable value masses",
        defaultEnabled = true,
    ),
    SHADOW_MASS(
        stage = 3,
        displayName = "Shadow Mass",
        blurb = "The big connected darks",
        defaultEnabled = true,
    ),
    LIGHT_MASS(
        stage = 4,
        displayName = "Light Mass",
        blurb = "Illuminated planes",
        defaultEnabled = true,
    ),
    CORE_SHADOWS(
        stage = 5,
        displayName = "Turning Form",
        blurb = "Core shadows and transitions",
        defaultEnabled = true,
    ),
    ACCENTS(
        stage = 6,
        displayName = "Accents",
        blurb = "Selective highlights",
        defaultEnabled = true,
    ),
    REFINE_EDGE(
        stage = 7,
        displayName = "Edge Control",
        blurb = "Hard, soft and lost edges",
        defaultEnabled = true,
    ),
    REFINE_DETAIL(
        stage = 7,
        displayName = "Detail Priority",
        blurb = "Where detail matters most",
        defaultEnabled = true,
    ),
    REFINE_TEXTURE(
        stage = 7,
        displayName = "Texture",
        blurb = "Surface variation",
        defaultEnabled = false,
    ),
    REFINE_COLOR(
        stage = 7,
        displayName = "Colour Shift",
        blurb = "Subtle colour changes",
        defaultEnabled = false,
    ),
    ;

    companion object {
        const val LAST_STAGE = 7

        val stageNumbers: List<Int> = (1..LAST_STAGE).toList()

        fun forStage(stage: Int): List<LayerId> = LayerId.entries.filter { it.stage == stage }

        val displayOrder: List<LayerId> get() = LayerId.entries.toList()

        fun stageTitle(stage: Int): String = when (stage) {
            1 -> "Drawing"
            2 -> "Block-In"
            3 -> "Shadow Mass"
            4 -> "Light Mass"
            5 -> "Core Shadows"
            6 -> "Accents"
            7 -> "Refinement"
            else -> "Stage $stage"
        }

        fun stagePurpose(stage: Int): String = when (stage) {
            1 -> "What to draw first: silhouette and major shapes."
            2 -> "Big, squint-readable value masses."
            3 -> "Where the major shadow family lives."
            4 -> "Where the major illuminated planes are."
            5 -> "Where the form turns between light and shadow."
            6 -> "Which highlights to place as accents."
            7 -> "What to refine, simplify, and how to control edges."
            else -> ""
        }
    }
}
