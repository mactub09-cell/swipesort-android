package com.swipesort.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** On-device text recognition. Never uploads screenshots or text. Call only on an IO dispatcher. */
internal class OfflineClassifier(private val context: Context) : AutoCloseable {
    private val tess = TessBaseAPI()

    init {
        val root = File(context.filesDir, "swipesort_ocr")
        val models = File(root, "tessdata").apply { mkdirs() }
        try {
            for (lang in listOf("rus", "eng")) {
                val dest = File(models, "$lang.traineddata")
                if (!dest.exists() || dest.length() < 100_000L) {
                    // Copy atomically to avoid leaving broken traineddata after an interrupted first run.
                    val temp = File(models, "$lang.tmp")
                    context.assets.open("tessdata/$lang.traineddata").use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    if (dest.exists()) dest.delete()
                    check(temp.renameTo(dest)) { "Не удалось сохранить OCR-модель $lang" }
                }
            }
            check(tess.init(root.absolutePath, "rus+eng")) {
                "Не удалось запустить распознавание русского и английского текста"
            }
        } catch (e: Exception) {
            tess.recycle()
            throw e
        }
    }

    /** Returns null when there is no convincing match, instead of inventing a category. */
    fun categoryOf(shot: Shot): String? {
        val bitmap = loadPreview(shot.uri) ?: return null
        return try {
            tess.setImage(bitmap)
            val recognized = tess.getUTF8Text().orEmpty()
            detectCategory(recognized)
        } finally {
            tess.clear()
            bitmap.recycle()
        }
    }

    private fun loadPreview(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    override fun close() {
        tess.recycle()
    }
}

