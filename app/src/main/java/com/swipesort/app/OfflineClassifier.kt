package com.swipesort.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

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

    /** Reuses the bitmap already decoded by HybridClassifier. */
    fun categoryOfBitmap(bitmap: Bitmap): String? {
        try {
            tess.setImage(bitmap)
            return detectCategory(tess.getUTF8Text().orEmpty())
        } finally {
            tess.clear()
        }
    }

    override fun close() {
        tess.recycle()
    }
}

