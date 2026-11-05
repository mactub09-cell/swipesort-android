package com.swipesort.app

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class Decision { NEW, KEEP, SKIPPED, PENDING_DELETE }

internal data class ScanReport(
    val visibleImages: Int,
    val screenshotCandidates: Int,
    val sampleFileNames: List<String>,
    val queryReturnedNull: Boolean = false
)

internal data class ScanResult(val shots: List<Shot>, val report: ScanReport)

internal data class Shot(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val date: Long,
    val category: String,
    val decision: Decision,
    val needsReview: Boolean = false
)

internal val groups = listOf(
    "Без категории", "Покупки", "Цены и скидки", "Чеки и платежи",
    "Рецепты", "Здоровье", "Работа и учёба", "Переписки",
    "Путешествия", "Красота и стиль", "Дом", "Дети и игры",
    "Мемы", "Полезные советы", "Документы", "Животные", "Еда и напитки", "Природа", "Дети и воспитание", "Другое"
)

internal fun hasPhotoAccess(context: Context): Boolean {
    val perms = if (Build.VERSION.SDK_INT >= 34) listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) else if (Build.VERSION.SDK_INT >= 33) listOf(
        Manifest.permission.READ_MEDIA_IMAGES
    ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    return perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

internal fun hasFullPhotoAccess(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

internal fun requestedPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal class ShotViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("shot_decisions", Context.MODE_PRIVATE)

    var shots by mutableStateOf<List<Shot>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var scanReport by mutableStateOf<ScanReport?>(null)
        private set
    var isClassifying by mutableStateOf(false)
        private set
    var ocrProcessed by mutableStateOf(0)
        private set
    var classificationStatus by mutableStateOf<String?>(null)
        private set
    private var classifyJob: Job? = null

    fun refresh() {
        val context = getApplication<Application>()
        if (!hasPhotoAccess(context)) {
            shots = emptyList()
            scanReport = null
            message = "Android пока не выдал приложению доступ к изображениям."
            return
        }
        viewModelScope.launch {
            isLoading = true
            message = null
            try {
                val result = withContext(Dispatchers.IO) { readScreenshots(context) }
                shots = result.shots
                ocrProcessed = result.shots.count { preferences.getBoolean("v05_done:${it.uri}", false) }
                scanReport = result.report
                message = when {
                    result.report.queryReturnedNull -> "Android не вернул список изображений (MediaStore query = null)."
                    result.shots.isEmpty() && result.report.visibleImages > 0 ->
                        "Изображения доступны, но ни один файл не распознан как скриншот по имени или папке. Это ограничение текущего поиска."
                    result.shots.isEmpty() -> "Android не вернул доступных изображений. Проверь, что разрешён доступ ко всем фото."
                    else -> null
                }
            } catch (e: Exception) {
                scanReport = null
                message = "Ошибка при чтении галереи: ${e.javaClass.simpleName}: ${e.localizedMessage ?: "нет описания"}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun readScreenshots(context: Context): ScanResult {
        val resolver = context.contentResolver
        val baseUri = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        val result = mutableListOf<Shot>()
        var visibleImages = 0
        val sampleNames = mutableListOf<String>()
        val cursor = resolver.query(baseUri, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")
            ?: return ScanResult(emptyList(), ScanReport(0, 0, emptyList(), queryReturnedNull = true))
        cursor.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx).orEmpty()
                visibleImages++
                if (sampleNames.size < 5) sampleNames += name.take(55)
                val path = cursor.getString(pathIdx).orEmpty()
                val bucket = cursor.getString(bucketIdx).orEmpty()
                val searchable = "$name $path $bucket".lowercase()
                val screenshotMarkers = listOf(
                    "screenshot", "screen_shot", "screen shot", "screenshots",
                    "screen capture", "screencapture", "снимок экрана", "скриншот"
                )
                if (screenshotMarkers.none { searchable.contains(it) }) continue
                val uri = ContentUris.withAppendedId(baseUri, id)
                val savedDecision = preferences.getString("decision:$uri", null)
                val decision = runCatching { Decision.valueOf(savedDecision ?: "NEW") }
                    .getOrDefault(Decision.NEW)
                val category = preferences.getString("category:$uri", "Без категории")
                    ?: "Без категории"
                result += Shot(
                    id = id, uri = uri, name = name,
                    size = cursor.getLong(sizeIdx).coerceAtLeast(0L),
                    date = cursor.getLong(dateIdx),
                    category = category,
                    decision = decision,
                    needsReview = preferences.getBoolean("category_needs_review:$uri", false)
                )
            }
        }
        return ScanResult(result, ScanReport(visibleImages, result.size, sampleNames))
    }

    /** Explicit local analysis, either a test batch or all remaining images. */
    fun classifyNextBatch(batchSize: Int = 40) {
        if (classifyJob?.isActive == true || isClassifying) return
        val context = getApplication<Application>()
        val candidates = shots.filter {
            it.category == "Без категории" &&
                !preferences.getBoolean("v05_done:${it.uri}", false) &&
                it.decision != Decision.PENDING_DELETE
        }.take(batchSize)
        if (candidates.isEmpty()) {
            classificationStatus = "Не осталось изображений для нового анализа. «Без категории» содержит неоднозначные файлы."
            return
        }
        classifyJob = viewModelScope.launch {
            isClassifying = true
            classificationStatus = "Загружаем локальные модели…"
            var identified = 0
            var needsReview = 0
            var unknown = 0
            var failures = 0
            var completedSoFar = ocrProcessed
            try {
                withContext(Dispatchers.IO) {
                    HybridClassifier(context).use { classifier ->
                        // Batch UI changes instead of rebuilding a list of 5,000 shots for every result.
                        val pending = mutableMapOf<Uri, Pair<String, Boolean>>()
                        suspend fun flush() {
                            if (pending.isEmpty()) return
                            val changes = pending.toMap()
                            pending.clear()
                            withContext(Dispatchers.Main) {
                                shots = shots.map { item ->
                                    val proposed = changes[item.uri]
                                    if (proposed != null && item.category == "Без категории")
                                        item.copy(category = proposed.first, needsReview = proposed.second)
                                    else item
                                }
                            }
                        }
                        candidates.forEachIndexed { index, shot ->
                            coroutineContext.ensureActive()
                            try {
                                // v0.5 re-reads OCR even for older v0.4 misses: fusion needs both signals.
                                val result = classifier.analyze(shot)
                                if (!result.analyzed) {
                                    failures++
                                } else {
                                    // Never overwrite a category manually chosen while analysis ran.
                                    val mayAssign = !preferences.getBoolean(
                                        "manual_category:${shot.uri}", false
                                    ) && preferences.getString(
                                        "category:${shot.uri}", "Без категории"
                                    ) == "Без категории"
                                    preferences.edit().apply {
                                        putBoolean("v05_done:${shot.uri}", true)
                                        if (result.ocrCompleted) putBoolean("ocr_done:${shot.uri}", true)
                                        if (result.category != null && mayAssign) {
                                            putString("category:${shot.uri}", result.category)
                                            putBoolean("category_needs_review:${shot.uri}", result.needsReview)
                                        }
                                        apply()
                                    }
                                    completedSoFar++
                                    if (result.category != null && mayAssign) {
                                        pending[shot.uri] = result.category to result.needsReview
                                        identified++
                                        if (result.needsReview) needsReview++
                                    } else {
                                        unknown++
                                    }
                                }
                            } catch (_: Exception) {
                                failures++
                            }
                            if ((index + 1) % 8 == 0 || index + 1 == candidates.size) flush()
                            if ((index + 1) % 8 == 0 || index + 1 == candidates.size) {
                                withContext(Dispatchers.Main) {
                                    ocrProcessed = completedSoFar
                                    classificationStatus = "Обработано ${index + 1} из ${candidates.size} · " +
                                        "найдено тем: $identified (проверить: $needsReview) · " +
                                        "без темы: $unknown · ошибок: $failures"
                                }
                            }
                        }
                    }
                }
                classificationStatus = "Готово: распределено $identified (проверить: $needsReview), " +
                    "без темы $unknown, ошибок $failures. " +
                    "Продолжи сортировку или проверь папки."
            } catch (cancel: CancellationException) {
                classificationStatus = "Остановлено. Уже обработанные результаты сохранены."
            } catch (e: Exception) {
                classificationStatus = "Не удалось запустить анализ: ${e.javaClass.simpleName}: " +
                    (e.localizedMessage ?: "нет описания")
            } finally {
                isClassifying = false
            }
        }
    }

    fun stopClassifying() {
        classifyJob?.cancel()
    }

    fun decide(shot: Shot, decision: Decision) {
        preferences.edit().putString("decision:${shot.uri}", decision.name).apply()
        shots = shots.map { if (it.uri == shot.uri) it.copy(decision = decision) else it }
    }

    fun decideMany(items: List<Shot>, decision: Decision) {
        val uris = items.map { it.uri.toString() }.toSet()
        preferences.edit().apply {
            uris.forEach { putString("decision:$it", decision.name) }
            apply()
        }
        shots = shots.map { if (it.uri.toString() in uris) it.copy(decision = decision) else it }
    }

    fun assign(shot: Shot, category: String) {
        preferences.edit()
            .putString("category:${shot.uri}", category)
            .putBoolean("manual_category:${shot.uri}", true)
            .remove("category_needs_review:${shot.uri}")
            .apply()
        shots = shots.map { if (it.uri == shot.uri) it.copy(category = category, needsReview = false) else it }
    }

    fun afterConfirmedTrash(items: List<Shot>) {
        preferences.edit().apply {
            items.forEach {
                remove("decision:${it.uri}")
                remove("category:${it.uri}")
                remove("ocr_done:${it.uri}")
                remove("v04_done:${it.uri}")
                remove("v05_done:${it.uri}")
                remove("manual_category:${it.uri}")
                remove("category_needs_review:${it.uri}")
            }
            apply()
        }
        val removed = items.map { it.uri }.toSet()
        shots = shots.filterNot { it.uri in removed }
        refresh()
    }
}
