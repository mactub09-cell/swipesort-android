package com.swipesort.app

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val model: ShotViewModel by viewModels()

    // 0 = no photo access, 1 = selected photos, 2 = all photos.
    // Compose observes this state and redraws the permissions screen immediately.
    private var photoAccessLevel by mutableIntStateOf(0)

    private fun refreshPhotoAccess() {
        photoAccessLevel = when {
            hasFullPhotoAccess(this) -> 2
            hasPhotoAccess(this) -> 1
            else -> 0
        }
        if (photoAccessLevel != 0) model.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoAccessLevel = when {
            hasFullPhotoAccess(this) -> 2
            hasPhotoAccess(this) -> 1
            else -> 0
        }
        setContent { SwipeSortApp(model, photoAccessLevel, ::refreshPhotoAccess) }
    }

    // Android permissions can change in Settings while the app is in the background.
    override fun onResume() {
        super.onResume()
        refreshPhotoAccess()
    }
}

private val accent = Color(0xFF6750C8)
private val panelLight = Color(0xFFF5F3FC)

@Composable
private fun SwipeSortApp(
    model: ShotViewModel,
    photoAccessLevel: Int,
    onPhotoPermissionResult: () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFFC7B7FF))
        else lightColorScheme(primary = accent, background = Color(0xFFFDFBFF), surface = Color.White)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewCategory by rememberSaveable { mutableStateOf("Все") }
    var pickCategoryFor by remember { mutableStateOf<Shot?>(null) }
    var inspectShot by remember { mutableStateOf<Shot?>(null) }
    var bulkCategory by remember { mutableStateOf<String?>(null) }
    var showTrashConfirmation by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<List<Shot>>(emptyList()) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPhotoPermissionResult() }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            model.afterConfirmedTrash(pendingConfirmation)
            Toast.makeText(context, "Файлы перемещены в системную корзину", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Удаление отменено. Файлы остались на месте", Toast.LENGTH_LONG).show()
        }
        pendingConfirmation = emptyList()
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                Surface(shadowElevation = 1.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("SwipeSort", fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text("Порядок в скриншотах · v0.2.0", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("✦", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    val menu = listOf(Triple("🗂️", "Категории", 0),
                        Triple("↔️", "Разбор", 1), Triple("🗑️", "Корзина", 2))
                    menu.forEach { (symbol, label, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index; openedCategory = null },
                            icon = { Text(symbol, fontSize = 22.sp) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                DiagnosticPanel(
                    context = context,
                    accessLevel = photoAccessLevel,
                    report = model.scanReport,
                    message = model.message,
                    expanded = diagnosticsExpanded,
                    onExpand = { diagnosticsExpanded = !diagnosticsExpanded },
                    onRecheck = onPhotoPermissionResult
                )
                if (photoAccessLevel == 0) {
                    PermissionContent(onRequest = {
                        permissionLauncher.launch(requestedPermissions())
                    })
                } else {
                    if (photoAccessLevel == 1) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Доступ только к выбранным фото", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(onClick = { permissionLauncher.launch(requestedPermissions()) }) {
                                    Text("Расширить", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (model.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    model.message?.let {
                        Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    }
                    when (tab) {
                        0 -> if (openedCategory == null) {
                            CategoryHome(model.shots,
                                onSelect = { openedCategory = it },
                                onReview = { reviewCategory = "Все"; tab = 1 },
                                onRefresh = { model.refresh() }
                            )
                        } else {
                            CategoryDetail(
                                category = openedCategory!!,
                                shots = matchingShots(model.shots, openedCategory!!),
                                onBack = { openedCategory = null },
                                onInspect = { inspectShot = it },
                                onReview = { reviewCategory = openedCategory!!; tab = 1; openedCategory = null },
                                onBulk = { bulkCategory = openedCategory }
                            )
                        }
                        1 -> ReviewScreen(
                            shots = model.shots,
                            chosenCategory = reviewCategory,
                            onChooseCategory = { reviewCategory = it },
                            onDecision = { shot, decision -> model.decide(shot, decision) },
                            onAssign = { pickCategoryFor = it }
                        )
                        else -> QueueScreen(
                            shots = model.shots.filter { it.decision == Decision.PENDING_DELETE },
                            onRestore = { model.decide(it, Decision.NEW) },
                            onTrash = { showTrashConfirmation = true }
                        )
                    }
                }
            }
        }

        val target = pickCategoryFor
        if (target != null) {
            CategoryPicker(onDismiss = { pickCategoryFor = null }) { category ->
                model.assign(target, category)
                pickCategoryFor = null
            }
        }

        val inspected = inspectShot
        if (inspected != null) {
            val actual = model.shots.firstOrNull { it.uri == inspected.uri }
            if (actual == null) inspectShot = null else ShotInspection(
                shot = actual,
                onClose = { inspectShot = null },
                onDecision = { model.decide(actual, it); inspectShot = null },
                onCategorize = { inspectShot = null; pickCategoryFor = actual }
            )
        }

        val categoryToDelete = bulkCategory
        if (categoryToDelete != null) {
            val targets = matchingShots(model.shots, categoryToDelete)
                .filter { it.decision != Decision.KEEP && it.decision != Decision.PENDING_DELETE }
            AlertDialog(
                onDismissRequest = { bulkCategory = null },
                title = { Text("Очистить категорию?") },
                text = { Text("${targets.size} файлов (${formatSize(targets.sumOf { it.size })}) попадут в очередь удаления. Уже сохранённые и ожидающие удаления не затрагиваются. Файлы пока не удаляются.") },
                confirmButton = {
                    TextButton(onClick = {
                        model.decideMany(targets, Decision.PENDING_DELETE)
                        bulkCategory = null
                    }, enabled = targets.isNotEmpty()) { Text("Добавить в очередь") }
                },
                dismissButton = { TextButton(onClick = { bulkCategory = null }) { Text("Отмена") } }
            )
        }

        if (showTrashConfirmation) {
            val queue = model.shots.filter { it.decision == Decision.PENDING_DELETE }
            val batch = queue.take(100)
            AlertDialog(
                onDismissRequest = { showTrashConfirmation = false },
                title = { Text("Переместить в корзину Android?") },
                text = { Text("Система запросит разрешение для ${batch.size} файлов (${formatSize(batch.sumOf { it.size })}). " +
                    (if (queue.size > 100) "В очереди ${queue.size}: остальные обработаем следующей партией. " else "") +
                    "После подтверждения файлы попадут в системную корзину, а не будут уничтожены безвозвратно.") },
                confirmButton = {
                    TextButton(onClick = {
                        showTrashConfirmation = false
                        if (batch.isNotEmpty()) try {
                            pendingConfirmation = batch
                            val request = MediaStore.createTrashRequest(
                                context.contentResolver, batch.map { it.uri }.toMutableList(), true
                            )
                            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        } catch (exception: Exception) {
                            pendingConfirmation = emptyList()
                            Toast.makeText(context,
                                "Ошибка доступа к корзине: ${exception.localizedMessage}",
                                Toast.LENGTH_LONG).show()
                        }
                    }, enabled = batch.isNotEmpty()) { Text("Продолжить") }
                },
                dismissButton = {
                    TextButton(onClick = { showTrashConfirmation = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun DiagnosticPanel(
    context: Context,
    accessLevel: Int,
    report: ScanReport?,
    message: String?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onRecheck: () -> Unit
) {
    val imgGrant = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED
    val selectedGrant = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
    val storageGrant = Build.VERSION.SDK_INT < 33 && ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    val accessText = when (accessLevel) {
        2 -> "полный"
        1 -> "выбранные фото"
        else -> "нет доступа"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            TextButton(onClick = onExpand) {
                Text("Диагностика v0.2.0 · $accessText ${if (expanded) "▲" else "▼"}")
            }
            if (expanded) {
                Text(
                    "Android API ${Build.VERSION.SDK_INT} · READ_MEDIA_IMAGES: $imgGrant · " +
                        "VISUAL_USER_SELECTED: $selectedGrant · READ_EXTERNAL_STORAGE: $storageGrant",
                    fontSize = 11.sp
                )
                Text(
                    "MediaStore: ${report?.visibleImages ?: "—"} изображений; " +
                        "скриншотов: ${report?.screenshotCandidates ?: "—"}",
                    fontSize = 12.sp
                )
                report?.sampleFileNames?.takeIf { it.isNotEmpty() }?.let { names ->
                    Text("Примеры имён: ${names.joinToString("; ")}", fontSize = 11.sp)
                }
                message?.let { Text("Статус: $it", fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRecheck) { Text("Проверить снова") }
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }) { Text("Разрешения Android") }
                }
            }
        }
    }
}

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🖼️", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text("Разберём скриншоты?", fontSize = 25.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Дай доступ к фотографиям. Мы найдём скриншоты на телефоне. Ничего не отправляем в интернет и не удаляем без твоего согласия.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(25.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ") }
    }
}

@Composable
private fun CategoryHome(shots: List<Shot>, onSelect: (String) -> Unit,
                         onReview: () -> Unit, onRefresh: () -> Unit) {
    val kept = shots.count { it.decision == Decision.KEEP }
    val queued = shots.count { it.decision == Decision.PENDING_DELETE }
    val finished = shots.count { it.decision != Decision.NEW }
    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Найдено скриншотов", fontSize = 14.sp)
                Text("${shots.size}", fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("Оставлено $kept · В очереди $queued", fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { if (shots.isEmpty()) 0f else finished.toFloat() / shots.size },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text("Разобрано $finished из ${shots.size}", fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth(),
                    enabled = shots.isNotEmpty()) { Text("Начать разбор →") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Мои категории", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh) { Text("Обновить") }
        }
        val cards = listOf("Все", "Без категории") + groups.drop(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cards, key = { it }) { cat ->
                val contents = matchingShots(shots, cat)
                Card(
                    onClick = { onSelect(cat) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.fillMaxWidth().height(104.dp)) {
                        if (contents.isNotEmpty()) AsyncImage(
                            model = contents.first().uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) else Box(Modifier.fillMaxSize().background(
                            MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text(categoryEmoji(cat), fontSize = 41.sp)
                        }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text(cat, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text("${contents.size} · ${formatSize(contents.sumOf { it.size })}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDetail(category: String, shots: List<Shot>,
                           onBack: () -> Unit, onInspect: (Shot) -> Unit,
                           onReview: () -> Unit, onBulk: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text(category, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
        }
        Text("${shots.size} файлов · ${formatSize(shots.sumOf { it.size })}",
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(shots, key = { it.uri.toString() }) { shot ->
                Box(
                    Modifier.fillMaxWidth().aspectRatio(.82f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspect(shot) }
                ) {
                    AsyncImage(model = shot.uri, contentDescription = shot.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(statusLabel(shot.decision), modifier = Modifier.align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(topStart = 8.dp))
                        .padding(5.dp), color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBulk, modifier = Modifier.weight(1f),
                enabled = shots.any { it.decision != Decision.KEEP && it.decision != Decision.PENDING_DELETE }) {
                Text("В очередь 🗑️", fontSize = 12.sp)
            }
            Button(onClick = onReview, modifier = Modifier.weight(1f),
                enabled = shots.any { it.decision == Decision.NEW }) {
                Text("Разобрать →", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReviewScreen(shots: List<Shot>, chosenCategory: String,
                         onChooseCategory: (String) -> Unit,
                         onDecision: (Shot, Decision) -> Unit,
                         onAssign: (Shot) -> Unit) {
    val candidates = matchingShots(shots, chosenCategory)
    val current = candidates.firstOrNull { it.decision == Decision.NEW }
    var showOptions by remember { mutableStateOf(false) }
    var dragX by remember(current?.id) { mutableFloatStateOf(0f) }
    var dragY by remember(current?.id) { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Разбор", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Box {
                TextButton(onClick = { showOptions = true }) {
                    Text("$chosenCategory ▾")
                }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    (listOf("Все") + groups).forEach { category ->
                        DropdownMenuItem(text = { Text(category) }, onClick = {
                            onChooseCategory(category); showOptions = false
                        })
                    }
                }
            }
        }
        if (current == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 65.sp)
                Text("Здесь всё разобрано", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Отложенные изображения можно вернуть через категории.",
                    textAlign = TextAlign.Center, fontSize = 14.sp)
            }
        } else {
            val count = candidates.count { it.decision == Decision.NEW }
            Text("Осталось $count · ← удалить / → оставить / ↑ отложить",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxSize()
                        .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                        .pointerInput(current.uri) {
                            detectDragGestures(
                                onDragEnd = {
                                    when {
                                        dragX < -110f -> onDecision(current, Decision.PENDING_DELETE)
                                        dragX > 110f -> onDecision(current, Decision.KEEP)
                                        dragY < -130f -> onDecision(current, Decision.SKIPPED)
                                    }
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDragCancel = { dragX = 0f; dragY = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    dragY += amount.y
                                }
                            )
                        },
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1B1924))) {
                        AsyncImage(
                            model = current.uri,
                            contentDescription = current.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (abs(dragX) > 35f || dragY < -35f) {
                            val label = when {
                                dragX < -35f -> "🗑 Удалить"
                                dragX > 35f -> "✓ Оставить"
                                else -> "↑ Отложить"
                            }
                            Text(label, modifier = Modifier.align(Alignment.TopCenter)
                                .padding(18.dp)
                                .background(Color.Black.copy(alpha = .85f), RoundedCornerShape(14.dp))
                                .padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            TextButton(onClick = { onAssign(current) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("🏷️ Категория: ${current.category} · изменить", fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision(current, Decision.PENDING_DELETE) },
                    modifier = Modifier.weight(1f)) { Text("🗑️ Удалить", fontSize = 12.sp) }
                OutlinedButton(onClick = { onDecision(current, Decision.SKIPPED) },
                    modifier = Modifier.weight(1f)) { Text("⏳ Позже", fontSize = 12.sp) }
                Button(onClick = { onDecision(current, Decision.KEEP) },
                    modifier = Modifier.weight(1f)) { Text("✓ Оставить", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QueueScreen(shots: List<Shot>, onRestore: (Shot) -> Unit, onTrash: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Очередь удаления", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("${shots.size} файлов · ${formatSize(shots.sumOf { it.size })}",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Пока ни один файл не удалён. Нажми «Вернуть», чтобы отменить выбор.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (shots.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("🧹", fontSize = 64.sp)
                Text("Здесь пока пусто", fontWeight = FontWeight.SemiBold)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shots, key = { it.uri.toString() }) { shot ->
                    Column {
                        AsyncImage(model = shot.uri, contentDescription = shot.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(.9f)
                                .clip(RoundedCornerShape(10.dp)))
                        TextButton(onClick = { onRestore(shot) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth()) { Text("↩ Вернуть", fontSize = 12.sp) }
                    }
                }
            }
        }
        Button(onClick = onTrash, enabled = shots.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("Переместить в корзину Android")
        }
    }
}

@Composable
private fun CategoryPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбери категорию") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    TextButton(onClick = { onPick(group) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${categoryEmoji(group)}  $group", modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ShotInspection(shot: Shot, onClose: () -> Unit,
                           onDecision: (Decision) -> Unit, onCategorize: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Скриншот") },
        text = {
            Column {
                AsyncImage(model = shot.uri, contentDescription = shot.name,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentScale = ContentScale.Fit)
                Spacer(Modifier.height(10.dp))
                Text("${shot.category} · ${formatSize(shot.size)}", fontSize = 13.sp)
                TextButton(onClick = onCategorize) { Text("Изменить категорию") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onDecision(Decision.NEW) }) { Text("↩ Вернуть") }
                    TextButton(onClick = { onDecision(Decision.KEEP) }) { Text("✓ Оставить") }
                }
                TextButton(onClick = { onDecision(Decision.PENDING_DELETE) },
                    modifier = Modifier.fillMaxWidth()) { Text("🗑️ В очередь удаления") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } }
    )
}

private fun matchingShots(shots: List<Shot>, category: String): List<Shot> =
    if (category == "Все") shots else shots.filter { it.category == category }

private fun statusLabel(decision: Decision): String = when (decision) {
    Decision.NEW -> "Новое"
    Decision.KEEP -> "✓"
    Decision.SKIPPED -> "⏳"
    Decision.PENDING_DELETE -> "🗑️"
}

private fun categoryEmoji(cat: String): String = when (cat) {
    "Все" -> "🗂️"
    "Без категории" -> "✨"
    "Покупки" -> "🛍️"
    "Рецепты" -> "🍓"
    "Переписки" -> "💬"
    "Работа и учёба" -> "📚"
    "Здоровье" -> "💊"
    "Документы" -> "📄"
    "Дети и игры" -> "🧸"
    "Путешествия" -> "✈️"
    "Дом" -> "🏠"
    else -> "📷"
}

private fun formatSize(size: Long): String = when {
    size >= 1024 * 1024 * 1024L -> "%.1f ГБ".format(size / (1024.0 * 1024 * 1024))
    size >= 1024 * 1024L -> "%.1f МБ".format(size / (1024.0 * 1024))
    size >= 1024L -> "%.1f КБ".format(size / 1024.0)
    else -> "$size Б"
}
