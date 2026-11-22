package com.proto.coloringbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proto.coloringbook.ui.theme.ColoringBookTheme
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

class ColoringState {
    var selectedTool by mutableStateOf(ColoringTool.BRUSH)
    var selectedColor by mutableStateOf(KidColorPalette[0])
    var brushSize by mutableStateOf(36f)

    var fillBitmap by mutableStateOf<Bitmap?>(null)
    var bitmapRevision by mutableStateOf(0)

    private var initialBitmap: Bitmap? = null
    private val bitmapHistory = mutableListOf<Bitmap>()

    val paths = mutableStateListOf<DrawnPath>()
    private val pathHistory = mutableListOf<List<DrawnPath>>()

    fun setInitialBitmap(bitmap: Bitmap) {
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
            setInitialBitmap(loaded)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColoringScreen(
    state: ColoringState = rememberColoringState()
) {
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { state.loadImage(context, it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Coloring Book",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFE082))
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = "Pick Image",
                            tint = Color(0xFF5D4037)
                        )
                        Text(
                            text = "Pick Image",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                    }
                },
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

@Composable
fun ColoringCanvas(
    state: ColoringState,
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(Unit) {
        if (state.fillBitmap == null) {
            state.setInitialBitmap(createDefaultColoringPageBitmap())
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(state.selectedTool, state.selectedColor, state.brushSize, scale, offsetX, offsetY) {
                if (state.selectedTool == ColoringTool.FILL) {
                    detectTapGestures { offset ->
                        val bmp = state.fillBitmap ?: return@detectTapGestures
                        if (scale > 0f) {
                            val bx = ((offset.x - offsetX) / scale).toInt()
                            val by = ((offset.y - offsetY) / scale).toInt()
                            if (bx in 0 until bmp.width && by in 0 until bmp.height) {
                                state.applyFloodFill(bx, by)
                            }
                        }
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints.add(change.position)
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                val isEraser = state.selectedTool == ColoringTool.ERASER
                                state.addPath(
                                    DrawnPath(
                                        path = createSmoothPath(currentPoints),
                                        color = if (isEraser) Color(0xFFFAF8F5) else state.selectedColor,
                                        strokeWidth = state.brushSize,
                                        isEraser = isEraser
                                    )
                                )
                            }
                            currentPoints.clear()
                        },
                        onDragCancel = {
                            currentPoints.clear()
                        }
                    )
                }
            }
    ) {
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
fun ColoringToolbar(
    state: ColoringState,
    modifier: Modifier = Modifier
) {
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
                        onClick = { state.selectedTool = ColoringTool.FILL }
                    )
                    ToolButton(
                        icon = Icons.Rounded.Brush,
                        contentDescription = "Brush",
                        selected = state.selectedTool == ColoringTool.BRUSH,
                        activeColor = Color(0xFFFF9500),
                        onClick = { state.selectedTool = ColoringTool.BRUSH }
                    )
                    ToolButton(
                        icon = Icons.Rounded.AutoFixNormal,
                        contentDescription = "Eraser",
                        selected = state.selectedTool == ColoringTool.ERASER,
                        activeColor = Color(0xFFFF2D55),
                        onClick = { state.selectedTool = ColoringTool.ERASER }
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
                        onSizeSelected = { state.brushSize = it }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        icon = Icons.Rounded.Undo,
                        contentDescription = "Undo",
                        containerColor = Color(0xFFE8F0FE),
                        iconTint = Color(0xFF1A73E8),
                        onClick = { state.undo() }
                    )
                    ActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear Canvas",
                        containerColor = Color(0xFFFCE8E6),
                        iconTint = Color(0xFFD93025),
                        onClick = { showClearDialog = true }
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
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This will erase all your colors on this page.",
                fontSize = 16.sp
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
