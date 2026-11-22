package com.proto.coloringbook

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proto.coloringbook.ui.theme.ColoringBookTheme
import org.json.JSONObject
import kotlin.math.abs

enum class ColoringTool {
    FILL, BRUSH, ERASER
}

data class DrawnPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

data class ColoringTemplate(
    val id: String,
    val title: String,
    val resourceName: String
)

data class TemplateCategory(
    val id: String,
    val name: String,
    val icon: String,
    val templates: List<ColoringTemplate>
)

class ColoringState {
    var selectedTool by mutableStateOf(ColoringTool.BRUSH)
    var selectedColor by mutableStateOf(KidColorPalette[0])
    var brushSize by mutableStateOf(36f)

    var currentTitle by mutableStateOf("Rainbow House")
    var fillBitmap by mutableStateOf<Bitmap?>(null)
    var bitmapRevision by mutableStateOf(0)

    private var initialBitmap: Bitmap? = null
    private val bitmapHistory = mutableListOf<Bitmap>()

    val paths = mutableStateListOf<DrawnPath>()
    private val pathHistory = mutableListOf<List<DrawnPath>>()

    fun setInitialBitmap(bitmap: Bitmap, title: String = "Coloring Page") {
        currentTitle = title
        initialBitmap = bitmap
        fillBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmapHistory.clear()
        paths.clear()
        pathHistory.clear()
        bitmapRevision++
    }

    fun loadImage(context: Context, uri: Uri) {
        val loaded = loadBitmapFromUri(context, uri)
        if (loaded != null) {
            setInitialBitmap(loaded, "My Gallery Image")
        }
    }

    fun addPath(drawnPath: DrawnPath) {
        pathHistory.add(paths.toList())
        paths.add(drawnPath)
    }

    fun applyFloodFill(startX: Int, startY: Int) {
        val bmp = fillBitmap ?: return
        bitmapHistory.add(bmp.copy(Bitmap.Config.ARGB_8888, true))
        performFloodFill(bmp, startX, startY, selectedColor.toArgb())
        bitmapRevision++
    }

    fun undo() {
        var undone = false
        if (paths.isNotEmpty()) {
            if (pathHistory.isNotEmpty()) {
                val previous = pathHistory.removeAt(pathHistory.lastIndex)
                paths.clear()
                paths.addAll(previous)
            } else {
                paths.removeAt(paths.lastIndex)
            }
            undone = true
        }
        if (!undone && bitmapHistory.isNotEmpty()) {
            val previousBmp = bitmapHistory.removeAt(bitmapHistory.lastIndex)
            fillBitmap = previousBmp
            bitmapRevision++
        }
    }

    fun clear() {
        initialBitmap?.let { initBmp ->
            fillBitmap?.let { currentBmp ->
                bitmapHistory.add(currentBmp.copy(Bitmap.Config.ARGB_8888, true))
            }
            fillBitmap = initBmp.copy(Bitmap.Config.ARGB_8888, true)
            bitmapRevision++
        }
        if (paths.isNotEmpty()) {
            pathHistory.add(paths.toList())
            paths.clear()
        }
    }
}

@Composable
fun rememberColoringState(): ColoringState {
    return remember { ColoringState() }
}

val KidColorPalette = listOf(
    Color(0xFFFF3B30), // Red
    Color(0xFFFF9500), // Orange
    Color(0xFFFFCC00), // Yellow
    Color(0xFF34C759), // Green
    Color(0xFF00C7BE), // Teal
    Color(0xFF007AFF), // Blue
    Color(0xFF5856D6), // Indigo
    Color(0xFFAF52DE), // Purple
    Color(0xFFFF2D55), // Pink
    Color(0xFFA2845E), // Brown
    Color(0xFF2C2C2E), // Black
    Color(0xFFFFFFFF)  // White
)

private val BrushSizes = listOf(18f, 36f, 60f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColoringBookTheme {
                ColoringScreen()
            }
        }
    }
}

private fun triggerLightVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(12)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColoringScreen(
    state: ColoringState = rememberColoringState()
) {
    val context = LocalContext.current
    var showCategoryPicker by remember { mutableStateOf(false) }

    val categories = remember { loadTemplateCategories(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { state.loadImage(context, it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🎨",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Coloring Book",
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 17.sp,
                                color = Color(0xFF2C2C2E),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Surface(
                            color = Color(0xFFFFECB3),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = state.currentTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFF5D4037),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        // Templates Category Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE8F5E9))
                                .clickable {
                                    triggerLightVibration(context)
                                    showCategoryPicker = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = "Categories",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Templates",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        // Gallery Picker Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFE082))
                                .clickable {
                                    triggerLightVibration(context)
                                    imagePickerLauncher.launch("image/*")
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Pick Image",
                                tint = Color(0xFF5D4037),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Gallery",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFF5D4037)
                            )
                        }

                        // Save Image Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE1F5FE))
                                .clickable {
                                    triggerLightVibration(context)
                                    val rendered = getRenderedCanvasBitmap(state)
                                    if (rendered != null) {
                                        val success = saveBitmapToGallery(context, rendered, state.currentTitle)
                                        val msg = if (success) "Saved to Pictures/ColoringBook!" else "Failed to save image"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = "Save Image",
                                tint = Color(0xFF0288D1),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Save",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFF0288D1)
                            )
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFFBF2)
                )
            )
        },
        bottomBar = {
            ColoringToolbar(state = state)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFAF8F5))
        ) {
            ColoringCanvas(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showCategoryPicker) {
        TemplateCategoryPickerSheet(
            categories = categories,
            onTemplateSelected = { template ->
                val bmp = loadTemplateBitmap(context, template)
                state.setInitialBitmap(bmp, template.title)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateCategoryPickerSheet(
    categories: List<TemplateCategory>,
    onTemplateSelected: (ColoringTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }

    val context = LocalContext.current
    val currentCategory = categories.find { it.id == selectedCategoryId } ?: categories.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select a Coloring Page",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF2C2C2E)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category.id == selectedCategoryId
                    val iconVector = when (category.icon) {
                        "font_download" -> Icons.Rounded.FontDownload
                        "pets" -> Icons.Rounded.Pets
                        "local_florist" -> Icons.Rounded.LocalFlorist
                        else -> Icons.Rounded.GridView
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            triggerLightVibration(context)
                            selectedCategoryId = category.id
                        },
                        label = {
                            Text(
                                text = category.name,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = category.name,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF007AFF),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentCategory != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentCategory.templates) { template ->
                        val thumbBitmap = remember(template.id) {
                            getTemplateThumbnail(context, template)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable {
                                    triggerLightVibration(context)
                                    onTemplateSelected(template)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF2)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    bitmap = thumbBitmap.asImageBitmap(),
                                    contentDescription = template.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                )
                                Text(
                                    text = template.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val thumbnailCache = mutableMapOf<String, Bitmap>()

private fun getTemplateThumbnail(context: Context, template: ColoringTemplate): Bitmap {
    return thumbnailCache.getOrPut(template.id) {
        val fullBmp = loadTemplateBitmap(context, template)
        Bitmap.createScaledBitmap(fullBmp, 200, 200, true)
    }
}

private fun loadTemplateCategories(context: Context): List<TemplateCategory> {
    val categories = mutableListOf<TemplateCategory>()
    try {
        val inputStream = context.resources.openRawResource(R.raw.coloring_templates)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val rootObj = JSONObject(jsonString)
        val jsonCategories = rootObj.getJSONArray("categories")

        for (i in 0 until jsonCategories.length()) {
            val catObj = jsonCategories.getJSONObject(i)
            val catId = catObj.getString("id")
            val catName = catObj.getString("name")
            val catIcon = catObj.optString("icon", "grid_view")

            val jsonTemplates = catObj.getJSONArray("templates")
            val templateList = mutableListOf<ColoringTemplate>()
            for (j in 0 until jsonTemplates.length()) {
                val tmplObj = jsonTemplates.getJSONObject(j)
                val id = tmplObj.getString("id")
                val title = tmplObj.getString("title")
                val resName = tmplObj.getString("resourceName")
                templateList.add(ColoringTemplate(id, title, resName))
            }
            categories.add(TemplateCategory(catId, catName, catIcon, templateList))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return categories
}

private fun loadTemplateBitmap(context: Context, template: ColoringTemplate): Bitmap {
    val drawableResId = context.resources.getIdentifier(
        template.resourceName.lowercase(), "drawable", context.packageName
    )
    val rawResId = if (drawableResId != 0) drawableResId else context.resources.getIdentifier(
        template.resourceName.lowercase(), "raw", context.packageName
    )

    if (rawResId != 0) {
        val decoded = BitmapFactory.decodeResource(context.resources, rawResId)
        if (decoded != null) {
            val aspect = decoded.width.toFloat() / decoded.height.toFloat()
            val (w, h) = if (aspect > 1f) 1080 to (1080 / aspect).toInt() else (1080 * aspect).toInt() to 1080
            val scaled = Bitmap.createScaledBitmap(decoded, w.coerceAtLeast(100), h.coerceAtLeast(100), true)
            return scaled.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    return createProceduralTemplateBitmap(template)
}

private fun createProceduralTemplateBitmap(template: ColoringTemplate): Bitmap {
    val id = template.id.lowercase()
    return when {
        id.startsWith("letter_") -> {
            val char = id.removePrefix("letter_").getOrNull(0)?.uppercaseChar() ?: 'A'
            createAlphabetLineArtBitmap(char)
        }
        id == "plant_rainbow_house" -> createDefaultColoringPageBitmap()
        id == "animal_lion" -> createLionLineArtBitmap()
        id == "animal_butterfly" -> createButterflyLineArtBitmap()
        id == "animal_fish" -> createFishLineArtBitmap()
        id == "plant_flower" -> createFlowerLineArtBitmap()
        id == "plant_tree" -> createTreeLineArtBitmap()
        id == "plant_sunflower" -> createSunflowerLineArtBitmap()
        else -> createAlphabetLineArtBitmap('A')
    }
}

private fun createAlphabetLineArtBitmap(char: Char, width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    val textPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 480f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    val textY = h / 2f + 160f
    canvas.drawText(char.toString(), w / 2f, textY, textPaint)

    val starPositions = listOf(
        Pair(w * 0.18f, h * 0.18f),
        Pair(w * 0.82f, h * 0.18f),
        Pair(w * 0.18f, h * 0.82f),
        Pair(w * 0.82f, h * 0.82f)
    )

    for ((sx, sy) in starPositions) {
        canvas.drawCircle(sx, sy, 35f, paint)
        canvas.drawCircle(sx, sy, 18f, paint)
    }

    return bitmap
}

private fun createButterflyLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    val bodyRect = RectF(w * 0.47f, h * 0.35f, w * 0.53f, h * 0.70f)
    canvas.drawOval(bodyRect, paint)

    canvas.drawCircle(w * 0.50f, h * 0.30f, 35f, paint)
    canvas.drawLine(w * 0.48f, h * 0.27f, w * 0.40f, h * 0.18f, paint)
    canvas.drawCircle(w * 0.40f, h * 0.18f, 12f, paint)
    canvas.drawLine(w * 0.52f, h * 0.27f, w * 0.60f, h * 0.18f, paint)
    canvas.drawCircle(w * 0.60f, h * 0.18f, 12f, paint)

    canvas.drawOval(RectF(w * 0.12f, h * 0.22f, w * 0.47f, h * 0.52f), paint)
    canvas.drawCircle(w * 0.30f, h * 0.37f, 40f, paint)

    canvas.drawOval(RectF(w * 0.18f, h * 0.50f, w * 0.47f, h * 0.75f), paint)
    canvas.drawCircle(w * 0.33f, h * 0.62f, 30f, paint)

    canvas.drawOval(RectF(w * 0.53f, h * 0.22f, w * 0.88f, h * 0.52f), paint)
    canvas.drawCircle(w * 0.70f, h * 0.37f, 40f, paint)

    canvas.drawOval(RectF(w * 0.53f, h * 0.50f, w * 0.82f, h * 0.75f), paint)
    canvas.drawCircle(w * 0.67f, h * 0.62f, 30f, paint)

    return bitmap
}

private fun createFishLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    canvas.drawOval(RectF(w * 0.25f, h * 0.30f, w * 0.75f, h * 0.70f), paint)

    val tailPath = AndroidPath().apply {
        moveTo(w * 0.75f, h * 0.50f)
        lineTo(w * 0.90f, h * 0.30f)
        lineTo(w * 0.90f, h * 0.70f)
        close()
    }
    canvas.drawPath(tailPath, paint)

    canvas.drawCircle(w * 0.38f, h * 0.45f, 22f, paint)
    canvas.drawCircle(w * 0.38f, h * 0.45f, 8f, paint)

    canvas.drawArc(RectF(w * 0.45f, h * 0.40f, w * 0.55f, h * 0.55f), 90f, 180f, false, paint)
    canvas.drawArc(RectF(w * 0.55f, h * 0.45f, w * 0.65f, h * 0.60f), 90f, 180f, false, paint)

    canvas.drawCircle(w * 0.20f, h * 0.30f, 24f, paint)
    canvas.drawCircle(w * 0.15f, h * 0.20f, 16f, paint)

    return bitmap
}

private fun createFlowerLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    canvas.drawLine(w * 0.50f, h * 0.50f, w * 0.50f, h * 0.85f, paint)
    canvas.drawOval(RectF(w * 0.50f, h * 0.65f, w * 0.68f, h * 0.75f), paint)

    canvas.drawCircle(w * 0.50f, h * 0.42f, 75f, paint)

    for (i in 0..5) {
        val angle = Math.toRadians((i * 60).toDouble())
        val px = (w * 0.50f + Math.cos(angle) * 145f).toFloat()
        val py = (h * 0.42f + Math.sin(angle) * 145f).toFloat()
        canvas.drawCircle(px, py, 60f, paint)
    }

    return bitmap
}

private fun createTreeLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    canvas.drawRect(w * 0.43f, h * 0.50f, w * 0.57f, h * 0.85f, paint)
    canvas.drawLine(w * 0.10f, h * 0.85f, w * 0.90f, h * 0.85f, paint)

    canvas.drawCircle(w * 0.35f, h * 0.40f, 100f, paint)
    canvas.drawCircle(w * 0.50f, h * 0.30f, 120f, paint)
    canvas.drawCircle(w * 0.65f, h * 0.40f, 100f, paint)
    canvas.drawCircle(w * 0.50f, h * 0.45f, 110f, paint)

    canvas.drawCircle(w * 0.38f, h * 0.35f, 22f, paint)
    canvas.drawCircle(w * 0.62f, h * 0.38f, 22f, paint)
    canvas.drawCircle(w * 0.50f, h * 0.25f, 22f, paint)

    return bitmap
}

private fun createSunflowerLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    return createFlowerLineArtBitmap(width, height)
}

private fun createLionLineArtBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    canvas.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, paint)

    for (i in 0..11) {
        val angle = Math.toRadians((i * 30).toDouble())
        val mx = (w * 0.50f + Math.cos(angle) * 220f).toFloat()
        val my = (h * 0.50f + Math.sin(angle) * 220f).toFloat()
        canvas.drawCircle(mx, my, 70f, paint)
    }

    canvas.drawCircle(w * 0.50f, h * 0.50f, 180f, paint)

    canvas.drawCircle(w * 0.36f, h * 0.36f, 40f, paint)
    canvas.drawCircle(w * 0.64f, h * 0.36f, 40f, paint)

    canvas.drawCircle(w * 0.42f, h * 0.46f, 20f, paint)
    canvas.drawCircle(w * 0.58f, h * 0.46f, 20f, paint)

    val nosePath = AndroidPath().apply {
        moveTo(w * 0.46f, h * 0.54f)
        lineTo(w * 0.54f, h * 0.54f)
        lineTo(w * 0.50f, h * 0.60f)
        close()
    }
    canvas.drawPath(nosePath, paint)

    return bitmap
}

private fun createDefaultColoringPageBitmap(width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val w = width.toFloat()
    val h = height.toFloat()

    val margin = 30f
    canvas.drawRoundRect(margin, margin, w - margin, h - margin, 40f, 40f, paint)

    canvas.drawCircle(w * 0.2f, h * 0.2f, w * 0.12f, paint)
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45).toDouble())
        val startX = (w * 0.2f + Math.cos(angle) * (w * 0.14f)).toFloat()
        val startY = (h * 0.2f + Math.sin(angle) * (w * 0.14f)).toFloat()
        val endX = (w * 0.2f + Math.cos(angle) * (w * 0.19f)).toFloat()
        val endY = (h * 0.2f + Math.sin(angle) * (w * 0.19f)).toFloat()
        canvas.drawLine(startX, startY, endX, endY, paint)
    }

    val rainbowRect = RectF(w * 0.08f, h * 0.10f, w * 0.92f, h * 0.85f)
    for (r in 0..5) {
        val inset = r * 30f
        val rect = RectF(
            rainbowRect.left + inset,
            rainbowRect.top + inset,
            rainbowRect.right - inset,
            rainbowRect.bottom - inset
        )
        canvas.drawArc(rect, 180f, 180f, false, paint)
    }

    val cloudPaint = Paint(paint)
    canvas.drawCircle(w * 0.18f, h * 0.62f, 45f, cloudPaint)
    canvas.drawCircle(w * 0.25f, h * 0.60f, 55f, cloudPaint)
    canvas.drawCircle(w * 0.32f, h * 0.63f, 45f, cloudPaint)

    canvas.drawCircle(w * 0.68f, h * 0.63f, 45f, cloudPaint)
    canvas.drawCircle(w * 0.75f, h * 0.60f, 55f, cloudPaint)
    canvas.drawCircle(w * 0.82f, h * 0.62f, 45f, cloudPaint)

    canvas.drawLine(w * 0.05f, h * 0.78f, w * 0.95f, h * 0.78f, paint)

    val houseLeft = w * 0.33f
    val houseRight = w * 0.67f
    val houseTop = h * 0.45f
    val houseBottom = h * 0.78f

    canvas.drawRect(houseLeft, houseTop, houseRight, houseBottom, paint)

    val roofPath = AndroidPath().apply {
        moveTo(houseLeft - 30f, houseTop)
        lineTo((houseLeft + houseRight) / 2f, houseTop - 120f)
        lineTo(houseRight + 30f, houseTop)
        close()
    }
    canvas.drawPath(roofPath, paint)

    val doorLeft = w * 0.46f
    val doorRight = w * 0.54f
    val doorTop = h * 0.62f
    canvas.drawRect(doorLeft, doorTop, doorRight, houseBottom, paint)
    canvas.drawCircle(w * 0.52f, h * 0.70f, 6f, paint)

    val win1Left = w * 0.36f
    val win1Right = w * 0.43f
    val win1Top = h * 0.52f
    val win1Bottom = h * 0.60f
    canvas.drawRect(win1Left, win1Top, win1Right, win1Bottom, paint)
    canvas.drawLine((win1Left + win1Right) / 2f, win1Top, (win1Left + win1Right) / 2f, win1Bottom, paint)
    canvas.drawLine(win1Left, (win1Top + win1Bottom) / 2f, win1Right, (win1Top + win1Bottom) / 2f, paint)

    val win2Left = w * 0.57f
    val win2Right = w * 0.64f
    val win2Top = h * 0.52f
    val win2Bottom = h * 0.60f
    canvas.drawRect(win2Left, win2Top, win2Right, win2Bottom, paint)
    canvas.drawLine((win2Left + win2Right) / 2f, win2Top, (win2Left + win2Right) / 2f, win2Bottom, paint)
    canvas.drawLine(win2Left, (win2Top + win2Bottom) / 2f, win2Right, (win2Top + win2Bottom) / 2f, paint)

    for (fx in listOf(w * 0.20f, w * 0.80f)) {
        canvas.drawLine(fx, h * 0.78f, fx, h * 0.84f, paint)
        canvas.drawCircle(fx, h * 0.84f, 16f, paint)
        for (a in 0..4) {
            val ang = Math.toRadians((a * 72).toDouble())
            val px = (fx + Math.cos(ang) * 25f).toFloat()
            val py = (h * 0.84f + Math.sin(ang) * 25f).toFloat()
            canvas.drawCircle(px, py, 10f, paint)
        }
    }

    return bitmap
}

@Composable
fun ColoringCanvas(
    state: ColoringState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPoints = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(Unit) {
        if (state.fillBitmap == null) {
            state.setInitialBitmap(createDefaultColoringPageBitmap(), "Rainbow House")
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    val currentScaleState by rememberUpdatedState(userScale)
    val currentOffsetState by rememberUpdatedState(userOffset)
    val currentToolState by rememberUpdatedState(state.selectedTool)
    val currentBmpState by rememberUpdatedState(state.fillBitmap)
    val currentBitmapScaleState by rememberUpdatedState(scale)
    val currentBitmapOffsetXState by rememberUpdatedState(offsetX)
    val currentBitmapOffsetYState by rememberUpdatedState(offsetY)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        var isMultiTouch = false
                        val dragPoints = mutableListOf<Offset>()

                        fun transformOffset(screenPos: Offset, uScale: Float, uOffset: Offset): Offset {
                            val cx = (screenPos.x - uOffset.x) / uScale
                            val cy = (screenPos.y - uOffset.y) / uScale
                            return Offset(cx, cy)
                        }

                        val activeTool = currentToolState
                        if (activeTool != ColoringTool.FILL) {
                            triggerLightVibration(context)
                            val p = transformOffset(firstDown.position, currentScaleState, currentOffsetState)
                            dragPoints.add(p)
                            currentPoints.clear()
                            currentPoints.add(p)
                        }

                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.size >= 2) {
                                isMultiTouch = true
                                currentPoints.clear()
                                dragPoints.clear()

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                val oldScale = currentScaleState
                                val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                                val effectiveZoom = newScale / oldScale

                                val oldOffset = currentOffsetState
                                val newOffset = if (newScale > 1f) {
                                    (oldOffset - centroid) * effectiveZoom + centroid + panChange
                                } else {
                                    Offset.Zero
                                }

                                userScale = newScale
                                userOffset = newOffset

                                changes.forEach { it.consume() }
                            } else if (!isMultiTouch && changes.size == 1) {
                                val change = changes[0]
                                if (change.pressed) {
                                    if (activeTool != ColoringTool.FILL) {
                                        change.consume()
                                        val pos = transformOffset(change.position, currentScaleState, currentOffsetState)
                                        dragPoints.add(pos)
                                        currentPoints.add(pos)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (!isMultiTouch) {
                            if (activeTool == ColoringTool.FILL) {
                                val bmp = currentBmpState
                                val bmpScale = currentBitmapScaleState
                                val bmpOffX = currentBitmapOffsetXState
                                val bmpOffY = currentBitmapOffsetYState

                                if (bmp != null && bmpScale > 0f) {
                                    val pos = transformOffset(firstDown.position, currentScaleState, currentOffsetState)
                                    val bx = ((pos.x - bmpOffX) / bmpScale).toInt()
                                    val by = ((pos.y - bmpOffY) / bmpScale).toInt()
                                    if (bx in 0 until bmp.width && by in 0 until bmp.height) {
                                        triggerLightVibration(context)
                                        state.applyFloodFill(bx, by)
                                    }
                                }
                            } else if (currentPoints.isNotEmpty()) {
                                val isEraser = activeTool == ColoringTool.ERASER
                                state.addPath(
                                    DrawnPath(
                                        path = createSmoothPath(currentPoints),
                                        color = if (isEraser) Color(0xFFFAF8F5) else state.selectedColor,
                                        strokeWidth = state.brushSize,
                                        isEraser = isEraser
                                    )
                                )
                                currentPoints.clear()
                            }
                        } else {
                            currentPoints.clear()
                        }
                    }
                }
        ) {
            withTransform({
                translate(userOffset.x, userOffset.y)
                scale(userScale, userScale, pivot = Offset.Zero)
            }) {
                state.fillBitmap?.let { bmp ->
                    @Suppress("UNUSED_VARIABLE")
                    val rev = state.bitmapRevision

                    val canvasW = size.width
                    val canvasH = size.height
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()

                    val s = minOf(canvasW / bmpW, canvasH / bmpH)
                    val ox = (canvasW - bmpW * s) / 2f
                    val oy = (canvasH - bmpH * s) / 2f

                    scale = s
                    offsetX = ox
                    offsetY = oy

                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                        dstSize = IntSize((bmpW * s).toInt(), (bmpH * s).toInt())
                    )
                }

                state.paths.forEach { drawnPath ->
                    if (drawnPath.strokeWidth == 0f) {
                        drawPath(
                            path = drawnPath.path,
                            color = drawnPath.color
                        )
                    } else {
                        drawPath(
                            path = drawnPath.path,
                            color = drawnPath.color,
                            style = Stroke(
                                width = drawnPath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (currentPoints.isNotEmpty()) {
                    val activePath = createSmoothPath(currentPoints)
                    val isEraser = state.selectedTool == ColoringTool.ERASER
                    drawPath(
                        path = activePath,
                        color = if (isEraser) Color(0xFFFAF8F5) else state.selectedColor,
                        style = Stroke(
                            width = state.brushSize,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        if (userScale > 1.05f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .clickable {
                        triggerLightVibration(context)
                        userScale = 1f
                        userOffset = Offset.Zero
                    },
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FitScreen,
                        contentDescription = "Reset Zoom",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Reset Zoom",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF007AFF)
                    )
                }
            }
        }
    }
}

private fun getRenderedCanvasBitmap(state: ColoringState): Bitmap? {
    val baseBmp = state.fillBitmap ?: return null
    val resultBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBmp)

    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    state.paths.forEach { drawnPath ->
        paint.color = drawnPath.color.toArgb()
        paint.strokeWidth = drawnPath.strokeWidth
        val androidPath = drawnPath.path.asAndroidPath()
        canvas.drawPath(androidPath, paint)
    }

    return resultBmp
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
    return try {
        val filename = "Coloring_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColoringBook")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun createSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        if (i == 1) {
            path.lineTo(midX, midY)
        } else {
            path.quadraticTo(p0.x, p0.y, midX, midY)
        }
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun performFloodFill(
    bitmap: Bitmap,
    startX: Int,
    startY: Int,
    fillColorInt: Int,
    tolerance: Int = 45
) {
    val width = bitmap.width
    val height = bitmap.height
    if (startX !in 0 until width || startY !in 0 until height) return

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val targetColor = pixels[startY * width + startX]

    if (isDarkPixel(targetColor)) return
    if (isSameColor(targetColor, fillColorInt, 10)) return

    val targetR = (targetColor shr 16) and 0xFF
    val targetG = (targetColor shr 8) and 0xFF
    val targetB = targetColor and 0xFF

    val queue = IntArray(width * height * 2)
    var head = 0
    var tail = 0

    queue[tail++] = startX
    queue[tail++] = startY
    pixels[startY * width + startX] = fillColorInt

    while (head < tail) {
        val x = queue[head++]
        val y = queue[head++]

        if (x > 0) tail = checkAndEnqueue(x - 1, y, width, pixels, queue, targetR, targetG, targetB, fillColorInt, tolerance, tail)
        if (x < width - 1) tail = checkAndEnqueue(x + 1, y, width, pixels, queue, targetR, targetG, targetB, fillColorInt, tolerance, tail)
        if (y > 0) tail = checkAndEnqueue(x, y - 1, width, pixels, queue, targetR, targetG, targetB, fillColorInt, tolerance, tail)
        if (y < height - 1) tail = checkAndEnqueue(x, y + 1, width, pixels, queue, targetR, targetG, targetB, fillColorInt, tolerance, tail)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun checkAndEnqueue(
    nx: Int,
    ny: Int,
    width: Int,
    pixels: IntArray,
    queue: IntArray,
    targetR: Int,
    targetG: Int,
    targetB: Int,
    fillColorInt: Int,
    tolerance: Int,
    currentTail: Int
): Int {
    var t = currentTail
    val idx = ny * width + nx
    val c = pixels[idx]

    if (c != fillColorInt) {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF

        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        if (lum >= 70 &&
            abs(r - targetR) <= tolerance &&
            abs(g - targetG) <= tolerance &&
            abs(b - targetB) <= tolerance
        ) {
            pixels[idx] = fillColorInt
            queue[t++] = nx
            queue[t++] = ny
        }
    }
    return t
}

private fun isDarkPixel(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    return lum < 70
}

private fun isSameColor(c1: Int, c2: Int, tolerance: Int): Boolean {
    val r1 = (c1 shr 16) and 0xFF
    val g1 = (c1 shr 8) and 0xFF
    val b1 = c1 and 0xFF

    val r2 = (c2 shr 16) and 0xFF
    val g2 = (c2 shr 8) and 0xFF
    val b2 = c2 and 0xFF

    return abs(r1 - r2) <= tolerance &&
            abs(g1 - g2) <= tolerance &&
            abs(b1 - b2) <= tolerance
}

private fun loadBitmapFromUri(context: Context, uri: Uri, targetWidth: Int = 1080, targetHeight: Int = 1080): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val decoded = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        decoded?.let { src ->
            val aspect = src.width.toFloat() / src.height.toFloat()
            val (w, h) = if (aspect > 1f) {
                targetWidth to (targetWidth / aspect).toInt()
            } else {
                (targetHeight * aspect).toInt() to targetHeight
            }
            val scaled = Bitmap.createScaledBitmap(src, w.coerceAtLeast(100), h.coerceAtLeast(100), true)
            scaled.copy(Bitmap.Config.ARGB_8888, true)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun ColoringToolbar(
    state: ColoringState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFFBF2),
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolButton(
                        icon = Icons.Rounded.FormatColorFill,
                        contentDescription = "Fill Bucket",
                        selected = state.selectedTool == ColoringTool.FILL,
                        activeColor = Color(0xFF007AFF),
                        onClick = {
                            triggerLightVibration(context)
                            state.selectedTool = ColoringTool.FILL
                        }
                    )
                    ToolButton(
                        icon = Icons.Rounded.Brush,
                        contentDescription = "Brush",
                        selected = state.selectedTool == ColoringTool.BRUSH,
                        activeColor = Color(0xFFFF9500),
                        onClick = {
                            triggerLightVibration(context)
                            state.selectedTool = ColoringTool.BRUSH
                        }
                    )
                    ToolButton(
                        icon = Icons.Rounded.AutoFixNormal,
                        contentDescription = "Eraser",
                        selected = state.selectedTool == ColoringTool.ERASER,
                        activeColor = Color(0xFFFF2D55),
                        onClick = {
                            triggerLightVibration(context)
                            state.selectedTool = ColoringTool.ERASER
                        }
                    )
                }

                AnimatedVisibility(visible = state.selectedTool != ColoringTool.FILL) {
                    BrushSizePicker(
                        selectedSize = state.brushSize,
                        dotColor = if (state.selectedTool == ColoringTool.ERASER) {
                            Color(0xFFFF2D55)
                        } else {
                            state.selectedColor
                        },
                        onSizeSelected = {
                            triggerLightVibration(context)
                            state.brushSize = it
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        icon = Icons.Rounded.Undo,
                        contentDescription = "Undo",
                        containerColor = Color(0xFFE8F0FE),
                        iconTint = Color(0xFF1A73E8),
                        onClick = {
                            triggerLightVibration(context)
                            state.undo()
                        }
                    )
                    ActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear Canvas",
                        containerColor = Color(0xFFFCE8E6),
                        iconTint = Color(0xFFD93025),
                        onClick = {
                            triggerLightVibration(context)
                            showClearDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                items(KidColorPalette) { color ->
                    val isSelected = state.selectedColor == color &&
                            state.selectedTool != ColoringTool.ERASER

                    AnimatedCrayon(
                        color = color,
                        selected = isSelected,
                        onClick = {
                            triggerLightVibration(context)
                            state.selectedColor = color
                            if (state.selectedTool == ColoringTool.ERASER) {
                                state.selectedTool = ColoringTool.BRUSH
                            }
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ClearConfirmationDialog(
            onConfirm = {
                triggerLightVibration(context)
                state.clear()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bgColor = if (selected) activeColor else Color.White
    val iconColor = if (selected) Color.White else Color(0xFF444444)

    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(if (selected) 6.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color(0xFFE0E0E0),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun BrushSizePicker(
    selectedSize: Float,
    dotColor: Color,
    onSizeSelected: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrushSizes.forEachIndexed { index, brushSize ->
            val dotDiameter = when (index) {
                0 -> 12.dp
                1 -> 18.dp
                else -> 24.dp
            }
            val isSelected = selectedSize == brushSize

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFEEEEEE) else Color.Transparent)
                    .clickable { onSizeSelected(brushSize) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dotDiameter)
                        .clip(CircleShape)
                        .background(if (dotColor == Color.White) Color.LightGray else dotColor)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun AnimatedCrayon(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val yOffset by animateDpAsState(
        targetValue = if (selected) (-16).dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "crayon_bounce"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(42.dp)
            .height(82.dp)
            .offset(y = yOffset)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tipHeight = h * 0.28f

            val tipPath = Path().apply {
                moveTo(w * 0.38f, 0f)
                lineTo(w * 0.62f, 0f)
                lineTo(w * 0.90f, tipHeight)
                lineTo(w * 0.10f, tipHeight)
                close()
            }
            drawPath(path = tipPath, color = color)

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.08f, tipHeight),
                size = Size(w * 0.84f, h - tipHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(w * 0.08f, tipHeight + h * 0.12f),
                size = Size(w * 0.84f, h * 0.28f)
            )

            drawRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.22f, tipHeight),
                size = Size(w * 0.14f, h - tipHeight)
            )

            if (color == Color.White) {
                drawRoundRect(
                    color = Color(0xFFBDBDBD),
                    topLeft = Offset(w * 0.08f, tipHeight),
                    size = Size(w * 0.84f, h - tipHeight),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
private fun ClearConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Start Over?",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFF2C2C2E)
            )
        },
        text = {
            Text(
                text = "This will erase all your colors on this page.",
                fontSize = 16.sp,
                fontFamily = FontFamily.SansSerif
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759))
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Yes, Clear",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "No, Keep",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    )
}
