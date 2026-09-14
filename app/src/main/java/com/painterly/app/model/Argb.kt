package com.painterly.app.model

/** Small helper for packing ARGB channels without touching android.graphics. */
object Argb {
    const val WHITE = -0x1 // 0xFFFFFFFF
    const val TRANSPARENT = 0

    fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF
}
