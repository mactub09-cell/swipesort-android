package com.swipesort.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.math.max

/** Two on-device models. No network calls or photo uploads. Must run on Dispatchers.IO. */
internal class HybridClassifier(private val context: Context) : AutoCloseable {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(.68f).build()
    )
    private var ocr: OfflineClassifier? = null

    data class Result(
        val category: String?,
        val method: String?,
        val ocrCompleted: Boolean,
        val analyzed: Boolean
    )

    fun analyze(shot: Shot, skipOcr: Boolean): Result {
        val bitmap = loadPreview(shot.uri) ?: return Result(null, null, false, false)
        try {
            var textCategory: String? = null
            var ocrCompleted = skipOcr
            if (!skipOcr) {
                try {
                    val engine = ocr ?: OfflineClassifier(context).also { ocr = it }
                    // We reuse the same bitmap for OCR and visual labeling.
                    textCategory = engine.categoryOfBitmap(bitmap)
                    ocrCompleted = true
                } catch (_: Exception) {
                    // A failed OCR pass should not disable the visual classifier.
                }
            }
            if (textCategory != null) return Result(textCategory, "Текст", ocrCompleted, true)
            return try {
                val labels = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)))
                    .map { VisualLabel(it.text, it.confidence) }
                val visualCategory = detectVisualCategory(labels)
                Result(visualCategory, if (visualCategory == null) null else "Изображение",
                    ocrCompleted, true)
            } catch (_: Exception) {
                // A successful OCR pass with no keywords still counts as analyzed.
                Result(null, null, ocrCompleted, ocrCompleted)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadPreview(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
        return resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
    }

    override fun close() {
        ocr?.close()
        labeler.close()
    }
}
