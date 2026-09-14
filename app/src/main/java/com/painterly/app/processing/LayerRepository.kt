package com.painterly.app.processing

import android.graphics.Bitmap
import com.painterly.app.model.LayerId
import com.painterly.app.model.StageParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of generated preview layers.
 *
 * Exactly one bitmap is kept per [LayerId]; the cache key records the parameter
 * signature so [ensure] knows when a layer is stale after a slider change.
 * Only the layers the caller asks for are generated, which keeps peak memory to
 * a single preview-resolution bitmap per layer.
 *
 * The maps are guarded by the intrinsic lock because composition reads them on
 * a different coroutine than the generator writes them.
 */
class LayerRepository {

    private val bitmaps = HashMap<LayerId, Bitmap>()
    private val signatures = HashMap<LayerId, String>()
    private var sourcePixels: IntArray? = null
    private var width: Int = 0
    private var height: Int = 0

    fun bindSource(pixels: IntArray, w: Int, h: Int) {
        synchronized(this) {
            bitmaps.clear()
            signatures.clear()
            sourcePixels = pixels
            width = w
            height = h
        }
    }

    fun clear() {
        synchronized(this) {
            bitmaps.clear()
            signatures.clear()
            sourcePixels = null
        }
    }

    fun isCurrent(layer: LayerId, params: StageParameters): Boolean = synchronized(this) {
        bitmaps[layer] != null && signatures[layer] == params.keyFor(layer)
    }

    fun peek(layer: LayerId): Bitmap? = synchronized(this) { bitmaps[layer] }

    /** Return a cached bitmap or generate (and cache) it on a background thread. */
    suspend fun ensure(layer: LayerId, params: StageParameters): Bitmap? {
        val signature = params.keyFor(layer)
        var pixelsSource: IntArray? = null
        var w = 0
        var h = 0
        synchronized(this) {
            val cached = bitmaps[layer]
            if (signatures[layer] == signature && cached != null) return cached
            pixelsSource = sourcePixels
            w = width
            h = height
        }
        val source = pixelsSource ?: return null
        if (w <= 0 || h <= 0) return null
        val bitmap = withContext(Dispatchers.Default) {
            val pixels = StageGenerators.generate(layer, source, w, h, params)
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
        synchronized(this) {
            bitmaps[layer] = bitmap
            signatures[layer] = signature
        }
        return bitmap
    }
}
