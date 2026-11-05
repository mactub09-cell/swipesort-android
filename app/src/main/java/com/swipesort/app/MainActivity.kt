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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

// SÖRT brand tokens: warm ivory, emerald, tulip red and restrained gold.
private val sortGreen = Color(0xFF07543C)
private val sortDeep = Color(0xFF163529)
private val sortIvory = Color(0xFFF9F3E9)
private val sortCream = Color(0xFFFFFBF5)
private val sortGold = Color(0xFFC59B4B)
private val sortRed = Color(0xFFBD3428)
private val sortMuted = Color(0xFF786F65)
private val sortLine = Color(0xFFE8DECF)
private val sortScheme = lightColorScheme(
    primary = sortGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EEE4),
    onPrimaryContainer = sortDeep,
    secondary = sortGold,
    onSecondary = sortDeep,
    secondaryContainer = Color(0xFFF4EAD8),
    onSecondaryContainer = sortDeep,
    background = sortIvory,
    onBackground = sortDeep,
    surface = sortCream,
    onSurface = sortDeep,
    surfaceVariant = Color(0xFFF0E8DB),
    onSurfaceVariant = sortMuted,
    error = sortRed
)


@Composable
private fun SwipeSortApp(
    model: ShotViewModel,
    photoAccessLevel: Int,
    onPhotoPermissionResult: () -> Unit
) {
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewCategory by remember { mutableStateOf("Все") }
    var pickCategoryFor by remember { mutableStateOf<Shot?>(null) }
    var inspectShot by remember { mutableStateOf<Shot?>(null) }
    var bulkCategory by remember { mutableStateOf<String?>(null) }
    var showTrashConfirmation by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<List<Shot>>(emptyList()) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

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

    MaterialTheme(colorScheme = sortScheme) {
        Scaffold(
            containerColor = sortIvory,
            topBar = {
                Surface(color = sortCream, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painter = painterResource(R.drawable.sort_mark),
                                contentDescription = "Эмблема SÖRT",
                                modifier = Modifier.size(44.dp))
                            Column {
                                Text("SÖRT", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                    color = sortGreen, letterSpacing = 1.sp)
                                Text("ПОРЯДОК В СКРИНШОТАХ", fontSize = 9.sp,
                                    color = sortDeep, letterSpacing = 1.sp)
                            }
                        }
                        IconButton(onClick = { diagnosticsExpanded = true },
                            modifier = Modifier.size(44.dp)) {
                            Icon(painterResource(R.drawable.sort_settings),
                                contentDescription = "Диагностика и настройки", tint = sortGreen)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = sortCream, tonalElevation = 2.dp) {
                    val menu = listOf(
                        Triple(R.drawable.sort_home, "Главная", 0),
                        Triple(R.drawable.sort_layers, "Разбор", 1),
                        Triple(R.drawable.sort_folder, "Категории", 2),
                        Triple(R.drawable.sort_trash, "Корзина", 3)
                    )
                    menu.forEach { (drawable, label, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = {
                                tab = index
                                openedCategory = null
                                if (index == 1) reviewCategory = "Все"
                            },
                            icon = {
                                Icon(painterResource(drawable), contentDescription = null,
                                    modifier = Modifier.size(23.dp))
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = sortGreen,
                                selectedTextColor = sortGreen,
                                indicatorColor = Color(0xFFE5E9D8),
                                unselectedIconColor = sortMuted,
                                unselectedTextColor = sortMuted
                            )
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
                        0 -> SortHomeScreen(
                            shots = model.shots,
                            onReview = { reviewCategory = "Все"; tab = 1 },
                            onCategories = { tab = 2 },
                            onAutoSort = { model.classifyNextBatch(40) },
                            onAutoSortAll = { model.classifyNextBatch(model.shots.size) },
                            onStopAutoSort = { model.stopClassifying() },
                            classificationActive = model.isClassifying,
                            classificationCompleted = model.ocrProcessed,
                            classificationRemaining = model.shots.count {
                                it.category == "Без категории" && it.decision != Decision.PENDING_DELETE &&
                                    !context.getSharedPreferences("shot_decisions", Context.MODE_PRIVATE)
                                        .getBoolean("v05_done:${it.uri}", false) &&
                                    !context.getSharedPreferences("shot_decisions", Context.MODE_PRIVATE)
                                        .getBoolean("manual_category:${it.uri}", false)
                            },
                            classificationStatus = model.classificationStatus
                        )
                        1 -> ReviewScreen(
                            shots = model.shots,
                            chosenCategory = reviewCategory,
                            onChooseCategory = { reviewCategory = it },
                            onDecision = { shot, decision -> model.decide(shot, decision) },
                            onAssign = { pickCategoryFor = it }
                        )
                        2 -> if (openedCategory == null) {
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
    if (expanded) {
        val imgGrant = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val selectedGrant = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        AlertDialog(
            onDismissRequest = onExpand,
            title = { Text("Диагностика SÖRT") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Android API ${Build.VERSION.SDK_INT}")
                    Text("Полный доступ: $imgGrant · выбранные: $selectedGrant")
                    Text("MediaStore: ${report?.visibleImages ?: "—"} изображений")
                    Text("Распознано как скриншоты: ${report?.screenshotCandidates ?: "—"}")
                    message?.let { Text("Ошибка: $it") }
                    report?.sampleFileNames?.let { names ->
                        Spacer(Modifier.height(12.dp))
                        Text("Примеры файлов:", fontWeight = FontWeight.Bold)
                        names.forEach { Text(it, fontSize = 11.sp) }
                    }
                    TextButton(onClick = { onRecheck(); onExpand() }) { Text("Пересканировать") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text("Разрешения Android") }
                }
            },
            confirmButton = { TextButton(onClick = onExpand) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.sort_mark), contentDescription = null,
            modifier = Modifier.size(90.dp))
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
private fun SortHomeScreen(
    shots: List<Shot>,
    onReview: () -> Unit,
    onCategories: () -> Unit,
    onAutoSort: () -> Unit,
    onAutoSortAll: () -> Unit,
    onStopAutoSort: () -> Unit,
    classificationActive: Boolean,
    classificationCompleted: Int,
    classificationRemaining: Int,
    classificationStatus: String?
) {
    val kept = shots.count { it.decision == Decision.KEEP }
    val queued = shots.count { it.decision == Decision.PENDING_DELETE }
    val finished = shots.count { it.decision != Decision.NEW }
    val filled = shots.count { it.category != "Без категории" }
    val progress = if (shots.isEmpty()) 0f else finished.toFloat() / shots.size

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = sortCream,
            shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(1.dp, sortLine)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFFCF6), Color(0xFFF4E9D7))))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(R.drawable.sort_mark), contentDescription = null,
                    modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(6.dp))
                Text("Давай наведём порядок\nв скриншотах?", color = sortDeep,
                    fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 29.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("✦  ✦  ✦", fontSize = 15.sp, color = sortGold)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onReview,
                    enabled = shots.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(painterResource(R.drawable.sort_layers), contentDescription = null,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Начать уборку", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("→", fontSize = 22.sp)
                }
                Spacer(Modifier.height(14.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(17.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sortLine)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${shots.size} скриншотов", fontWeight = FontWeight.Bold,
                                color = sortDeep, fontSize = 16.sp)
                            Text(formatSize(shots.sumOf { it.size }), color = sortMuted, fontSize = 12.sp)
                        }
                        Icon(painterResource(R.drawable.sort_folder), contentDescription = null,
                            tint = sortGreen)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, sortLine)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Прогресс уборки", fontWeight = FontWeight.Bold, color = sortDeep)
                    Text("$finished / ${shots.size}", color = sortMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = sortGreen, trackColor = sortLine)
                Spacer(Modifier.height(12.dp))
                Text("Оставлено $kept  ·  В очереди удаления $queued", color = sortMuted, fontSize = 12.sp)
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, sortLine)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✦", color = sortGold, fontSize = 24.sp)
                    Text("Умная сортировка", color = sortDeep,
                        fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Text("Распознаёт текст и объекты локально. Фотографии не отправляются в интернет.",
                    color = sortMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("По темам: $filled  ·  Проверено: $classificationCompleted",
                    color = sortDeep, fontSize = 13.sp)
                Text("Ожидают обработки: $classificationRemaining", color = sortMuted, fontSize = 12.sp)
                classificationStatus?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = sortMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (classificationActive) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = sortGreen)
                    TextButton(onClick = onStopAutoSort) { Text("Остановить") }
                } else {
                    Button(onClick = onAutoSort, enabled = classificationRemaining > 0,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(50)) {
                        Text("Проверить 40 скриншотов")
                    }
                    OutlinedButton(onClick = onAutoSortAll, enabled = classificationRemaining > 0,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                        Text("Сортировать все оставшиеся")
                    }
                    Text("Большие партии требуют времени. Результаты сохраняются постепенно.",
                        color = sortMuted, fontSize = 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Тематические папки", color = sortDeep,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCategories) { Text("Все категории →", fontSize = 13.sp) }
        }
        val popular = groups.filter { c -> shots.any { it.category == c } }.take(2)
        if (popular.isEmpty()) {
            Text("После анализа здесь появятся твои категории.", color = sortMuted, fontSize = 13.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                popular.forEach { category ->
                    Box(Modifier.weight(1f)) {
                        SortCategoryTile(category, matchingShots(shots, category), onCategories)
                    }
                }
                if (popular.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryHome(
    shots: List<Shot>,
    onSelect: (String) -> Unit,
    onReview: () -> Unit,
    onRefresh: () -> Unit
) {
    val cards = listOf("Все", "Без категории") + groups.drop(1)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Категории", color = sortDeep, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Найди нужное за секунды", color = sortMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onRefresh) { Text("Обновить") }
            }
        }
        items(cards, key = { it }) { category ->
            SortCategoryTile(category, matchingShots(shots, category)) { onSelect(category) }
        }
    }
}

@Composable
private fun SortCategoryTile(category: String, contents: List<Shot>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = sortCream),
        border = androidx.compose.foundation.BorderStroke(1.dp, sortLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            if (contents.isNotEmpty()) {
                AsyncImage(model = contents.first().uri, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF1E8D8)),
                    contentAlignment = Alignment.Center) {
                    Text(categoryEmoji(category), fontSize = 38.sp)
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(category, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = sortDeep, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${contents.size} · ${formatSize(contents.sumOf { it.size })}",
                color = sortMuted, fontSize = 11.sp)
            val reviewCount = contents.count { it.needsReview }
            if (reviewCount > 0) Text("Проверить: $reviewCount", fontSize = 11.sp,
                color = sortRed)
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
                overflow = TextOverflow.Ellipsis, fontSize = 20.sp, color = sortDeep)
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
                    Text(if (shot.needsReview) "? Проверить" else statusLabel(shot.decision),
                        modifier = Modifier.align(Alignment.BottomEnd)
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
            Text("Разбор", fontWeight = FontWeight.Bold, fontSize = 25.sp, color = sortDeep)
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
                Text(if (chosenCategory == "Все") "🎉" else "📂", fontSize = 65.sp)
                Text(if (chosenCategory == "Все") "Пока нет новых для разбора"
                     else "В категории «$chosenCategory» нет новых скриншотов",
                     fontSize = 19.sp, fontWeight = FontWeight.Bold,
                     textAlign = TextAlign.Center)
                if (chosenCategory != "Все") {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onChooseCategory("Все") }) {
                        Text("Показать все скриншоты (${shots.count { it.decision == Decision.NEW }})")
                    }
                } else {
                    Text("Оставленные и отложенные фотографии доступны через категории.",
                        textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        } else {
            val count = candidates.count { it.decision == Decision.NEW }
            Text("Осталось $count · из ${candidates.size}", color = sortMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = {
                if (candidates.isEmpty()) 0f
                else (candidates.size - count).toFloat() / candidates.size
            }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = sortGreen, trackColor = sortLine)
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
                    shape = RoundedCornerShape(23.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sortGold.copy(alpha = .65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(sortCream)) {
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
            Surface(
                onClick = { onAssign(current) },
                color = sortCream, shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, sortLine),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Категория: ${current.category}  ▾", fontSize = 12.sp,
                    color = sortDeep, modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                SortDecisionButton(R.drawable.sort_trash, "Удалить",
                    sortRed, Color(0xFFFCEAE5)) {
                    onDecision(current, Decision.PENDING_DELETE)
                }
                SortDecisionButton(R.drawable.sort_clock, "Позже",
                    sortDeep, Color(0xFFF0EBE1)) {
                    onDecision(current, Decision.SKIPPED)
                }
                SortDecisionButton(R.drawable.sort_check, "Оставить",
                    sortGreen, Color(0xFFDEEEE2)) {
                    onDecision(current, Decision.KEEP)
                }
            }
        }
    }
}

@Composable
private fun QueueScreen(shots: List<Shot>, onRestore: (Shot) -> Unit, onTrash: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Корзина", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = sortDeep)
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
                Image(painterResource(R.drawable.sort_mark), contentDescription = null,
                    modifier = Modifier.size(116.dp))
                Spacer(Modifier.height(12.dp))
                Text("В корзине 0 файлов", fontSize = 21.sp,
                    color = sortDeep, fontWeight = FontWeight.Bold)
                Text("Здесь будут скриншоты, которые ты решишь удалить",
                    textAlign = TextAlign.Center, color = sortMuted, fontSize = 13.sp)
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
            Icon(painterResource(R.drawable.sort_trash), contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Переместить в корзину Android")
        }
    }
}

@Composable
private fun SortDecisionButton(
    drawable: Int,
    title: String,
    color: Color,
    background: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, color = background, shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .16f)),
            modifier = Modifier.size(60.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(drawable), contentDescription = title,
                    tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                Text("${shot.category}${if (shot.needsReview) " · предложено ИИ, проверь" else ""} · ${formatSize(shot.size)}", fontSize = 13.sp)
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
    "Дети и воспитание" -> "👨‍👩‍👧"
    "Животные" -> "🐈"
    "Еда и напитки" -> "🍰"
    "Природа" -> "🌿"
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
