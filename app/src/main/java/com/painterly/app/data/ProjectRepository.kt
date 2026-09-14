package com.painterly.app.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.painterly.app.imaging.BitmapLoader
import com.painterly.app.model.PaintingStage
import com.painterly.app.model.Project
import com.painterly.app.model.RefineView
import com.painterly.app.model.StageSettings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Local, account-free project storage. Everything lives under files/projects/<id>/:
 * reference image, thumbnail, JSON metadata and a PNG stage cache keyed by settings signature.
 */
class ProjectRepository private constructor(private val context: Context) {

    companion object {
        private const val THUMBNAIL_MAX = 480

        @Volatile
        private var instance: ProjectRepository? = null

        fun get(context: Context): ProjectRepository =
            instance ?: synchronized(this) {
                instance ?: ProjectRepository(context.applicationContext).also { instance = it }
            }
    }

    fun referenceMaxDimension(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val large = am?.largeMemoryClass ?: 256
        return when {
            large >= 512 -> 1800
            large >= 256 -> 1400
            else -> 1024
        }
    }

    private fun rootDir(): File = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(id: String): File = File(rootDir(), id)
    fun referenceFile(id: String): File = File(projectDir(id), "reference.jpg")
    fun thumbnailFile(id: String): File = File(projectDir(id), "thumb.jpg")
    private fun metaFile(id: String): File = File(projectDir(id), "project.json")
    private fun cacheDir(id: String): File = File(projectDir(id), "cache")
    private fun layerFile(id: String, stage: PaintingStage): File =
        File(cacheDir(id), "layer_${stage.name}.png")

    private fun signatureFile(id: String): File = File(cacheDir(id), "signature.txt")

    // ------------------------------------------------------------------ create / read

    fun createProject(uri: Uri, name: String): Project {
        val id = UUID.randomUUID().toString()
        projectDir(id).mkdirs()
        // Copy the picked stream to a local file once, so decoding does not depend on the
        // provider allowing the stream to be reopened. This is the most common cause of
        // "could not import" failures across gallery/photo providers.
        val temp = File(projectDir(id), "import_tmp")
        try {
            val opened = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not read the selected image")
            opened.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (temp.length() == 0L) throw IOException("The selected image is empty")

            val reference = BitmapLoader.decodeNormalized(temp, referenceMaxDimension())
                ?: throw IOException("Unsupported or corrupt image")
            try {
                FileOutputStream(referenceFile(id)).use {
                    reference.compress(Bitmap.CompressFormat.JPEG, 92, it)
                }
                val thumb = BitmapLoader.scaleToFit(reference, THUMBNAIL_MAX)
                try {
                    FileOutputStream(thumbnailFile(id)).use {
                        thumb.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    }
                } finally {
                    if (thumb !== reference) thumb.recycle()
                }
                val now = System.currentTimeMillis()
                val project = Project(
                    id = id,
                    name = name.ifBlank { "Untitled Painting" },
                    createdAt = now,
                    updatedAt = now,
                    referenceWidth = reference.width,
                    referenceHeight = reference.height,
                )
                saveProject(project)
                return project
            } finally {
                reference.recycle()
            }
        } catch (e: Exception) {
            projectDir(id).deleteRecursively()
            throw e
        } finally {
            temp.delete()
        }
    }

    fun listProjects(): List<Project> = rootDir().listFiles()
        ?.filter { it.isDirectory && metaFile(it.name).exists() }
        ?.mapNotNull { runCatching { readProject(it.name) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun loadProject(id: String): Project? = runCatching { readProject(id) }.getOrNull()

    fun saveProject(project: Project) {
        projectDir(project.id).mkdirs()
        metaFile(project.id).writeText(toJson(project).toString())
    }

    fun deleteProject(id: String) {
        projectDir(id).deleteRecursively()
    }

    fun loadReference(project: Project): Bitmap? {
        val bitmap = BitmapLoader.decodeNormalized(referenceFile(project.id), referenceMaxDimension())
        if (bitmap != null) return bitmap
        return BitmapFactory.decodeFile(thumbnailFile(project.id).absolutePath)
    }

    fun loadThumbnail(project: Project): Bitmap? {
        val file = thumbnailFile(project.id)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = 2
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    // ------------------------------------------------------------------ layer cache

    fun cacheSignature(id: String): String? =
        signatureFile(id).takeIf { it.exists() }?.readText()

    fun loadCachedLayers(id: String): Map<PaintingStage, Bitmap>? {
        val result = LinkedHashMap<PaintingStage, Bitmap>()
        for (stage in PaintingStage.entries) {
            val file = layerFile(id, stage)
            if (!file.exists()) {
                result.values.forEach { it.recycle() }
                return null
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap == null) {
                result.values.forEach { it.recycle() }
                return null
            }
            result[stage] = bitmap
        }
        return result
    }

    fun saveLayers(id: String, layers: Map<PaintingStage, Bitmap>, signature: String) {
        val dir = cacheDir(id).apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name.startsWith("layer_")) it.delete() }
        for ((stage, bitmap) in layers) {
            runCatching {
                FileOutputStream(layerFile(id, stage)).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        signatureFile(id).writeText(signature)
    }

    // ------------------------------------------------------------------ json

    private fun readProject(id: String): Project {
        val json = JSONObject(metaFile(id).readText())
        val settingsJson = json.optJSONObject("settings") ?: JSONObject()
        val settings = StageSettings(
            drawDetail = settingsJson.optDouble("drawDetail", 0.5).toFloat(),
            blockInValues = settingsJson.optInt("blockInValues", 4).coerceIn(3, 4),
            shadowAmount = settingsJson.optDouble("shadowAmount", 0.5).toFloat(),
            lightAmount = settingsJson.optDouble("lightAmount", 0.5).toFloat(),
            formAmount = settingsJson.optDouble("formAmount", 0.5).toFloat(),
            accentAmount = settingsJson.optDouble("accentAmount", 0.5).toFloat(),
            refineView = runCatching {
                RefineView.valueOf(settingsJson.optString("refineView", RefineView.EDGES.name))
            }.getOrDefault(RefineView.EDGES),
            refineAmount = settingsJson.optDouble("refineAmount", 0.5).toFloat(),
        )
        return Project(
            id = json.getString("id"),
            name = json.optString("name", "Untitled Painting"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            referenceWidth = json.optInt("referenceWidth", 1),
            referenceHeight = json.optInt("referenceHeight", 1),
            settings = settings,
            lastStage = runCatching {
                PaintingStage.valueOf(json.optString("lastStage", PaintingStage.DRAW.name))
            }.getOrDefault(PaintingStage.DRAW),
        )
    }

    private fun toJson(project: Project): JSONObject = JSONObject().apply {
        put("id", project.id)
        put("name", project.name)
        put("createdAt", project.createdAt)
        put("updatedAt", project.updatedAt)
        put("referenceWidth", project.referenceWidth)
        put("referenceHeight", project.referenceHeight)
        put("lastStage", project.lastStage.name)
        put(
            "settings",
            JSONObject().apply {
                put("drawDetail", project.settings.drawDetail.toDouble())
                put("blockInValues", project.settings.blockInValues)
                put("shadowAmount", project.settings.shadowAmount.toDouble())
                put("lightAmount", project.settings.lightAmount.toDouble())
                put("formAmount", project.settings.formAmount.toDouble())
                put("accentAmount", project.settings.accentAmount.toDouble())
                put("refineView", project.settings.refineView.name)
                put("refineAmount", project.settings.refineAmount.toDouble())
            },
        )
    }
}
