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
        val analyzed: Boolean,
        val needsReview: Boolean = false
    )

    fun analyze(shot: Shot): Result {
        val bitmap = loadPreview(shot.uri) ?: return Result(null, null, false, false)
        try {
            var recognizedText = ""
            var ocrCompleted = false
            try {
                val engine = ocr ?: OfflineClassifier(context).also { ocr = it }
                recognizedText = engine.readText(bitmap)
                ocrCompleted = true
            } catch (_: Exception) {
                // Do not block local image labeling when OCR is unavailable.
            }
            val labels = try {
                Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)))
                    .map { VisualLabel(it.text, it.confidence) }
            } catch (_: Exception) { emptyList() }
            val proposal = rankCategory(recognizedText, labels)
            return Result(proposal?.category, proposal?.explanation, ocrCompleted,
                ocrCompleted || labels.isNotEmpty(), proposal?.needsReview ?: false)
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
