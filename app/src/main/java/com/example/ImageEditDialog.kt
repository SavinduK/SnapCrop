package com.example

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class EditTool {
    PEN,
    ERASER,
    TEXT
}

data class StrokePoint(val x: Float, val y: Float)

data class DrawnStroke(
    val points: List<StrokePoint>,
    val color: Color,
    val strokeWidthDp: Float
)

data class TextOverlayItem(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    var text: String,
    var xPercent: Float, // 0..1 relative to canvas width
    var yPercent: Float, // 0..1 relative to canvas height
    var color: Color = Color.White,
    var fontSizeSp: Float = 20f
)

/**
 * Dedicated Image Editing Modal/Window
 *
 * Supported Editing Tools:
 * - Pen Tool: Freehand drawing/annotation with color palette and stroke widths
 * - Eraser: Erase drawn strokes or clear all annotations
 * - Text Box: Add draggable, customizable text overlays onto the image
 */
@Composable
fun ImageEditDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    val density = LocalDensity.current

    // Active tool state
    var currentTool by remember { mutableStateOf(EditTool.PEN) }

    // Pen styling
    val penColors = remember {
        listOf(
            Color(0xFF00E5FF), // Cyan
            Color(0xFFFF3B30), // Red
            Color(0xFFFFCC00), // Yellow
            Color(0xFF34C759), // Green
            Color(0xFFFFFFFF), // White
            Color(0xFF0F172A)  // Dark Slate
        )
    }
    var selectedColor by remember { mutableStateOf(penColors[0]) }
    var strokeWidthDp by remember { mutableFloatStateOf(5f) }

    // Strokes & Text
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    val textOverlays = remember { mutableStateListOf<TextOverlayItem>() }

    // Text creation dialog
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var selectedTextId by remember { mutableStateOf<Long?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D16))
                .testTag("image_editing_window"),
            color = Color(0xFF090D16)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Bar: Cancel, Title, Undo, Clear, Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .testTag("btn_edit_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Edit Screenshot",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Undo action
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    strokes.removeAt(strokes.lastIndex)
                                } else if (textOverlays.isNotEmpty()) {
                                    textOverlays.removeAt(textOverlays.lastIndex)
                                }
                            },
                            enabled = strokes.isNotEmpty() || textOverlays.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (strokes.isNotEmpty() || textOverlays.isNotEmpty()) Color(0x28FFFFFF) else Color(0x10FFFFFF))
                                .testTag("btn_edit_undo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (strokes.isNotEmpty() || textOverlays.isNotEmpty()) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Clear all annotations
                        IconButton(
                            onClick = {
                                strokes.clear()
                                textOverlays.clear()
                            },
                            enabled = strokes.isNotEmpty() || textOverlays.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (strokes.isNotEmpty() || textOverlays.isNotEmpty()) Color(0x28FFFFFF) else Color(0x10FFFFFF))
                                .testTag("btn_edit_clear_all")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = if (strokes.isNotEmpty() || textOverlays.isNotEmpty()) Color(0xFFFF6B6B) else Color(0x55FFFFFF),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Done / Save Edits
                        Surface(
                            onClick = {
                                val editedBitmap = renderEditedBitmap(
                                    sourceBitmap = bitmap,
                                    strokes = strokes,
                                    textOverlays = textOverlays
                                )
                                onDone(editedBitmap)
                            },
                            shape = RoundedCornerShape(percent = 50),
                            color = Color(0xFF00E5FF),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("btn_edit_done")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = Color(0xFF090D16),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Done",
                                    color = Color(0xFF090D16),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Middle Canvas: Image + Annotation layer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imgAspect = (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.2f, 5f)

                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(imgAspect)
                            .clipToBounds()
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val canvasW = constraints.maxWidth.toFloat()
                        val canvasH = constraints.maxHeight.toFloat()

                        // Base Image
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Editing image",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Drawing Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("canvas_image_edit")
                                .pointerInput(currentTool, selectedColor, strokeWidthDp) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            when (currentTool) {
                                                EditTool.PEN -> {
                                                    val normalizedX = (offset.x / canvasW).coerceIn(0f, 1f)
                                                    val normalizedY = (offset.y / canvasH).coerceIn(0f, 1f)
                                                    currentStrokePoints = listOf(StrokePoint(normalizedX, normalizedY))
                                                }
                                                EditTool.ERASER -> {
                                                    // Erase stroke near tap/touch
                                                    eraseStrokesNear(
                                                        touchX = offset.x / canvasW,
                                                        touchY = offset.y / canvasH,
                                                        strokes = strokes
                                                    )
                                                }
                                                EditTool.TEXT -> {
                                                    // Tap on canvas in text mode to add text at this position
                                                    textInput = ""
                                                    showAddTextDialog = true
                                                }
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pos = change.position
                                            when (currentTool) {
                                                EditTool.PEN -> {
                                                    val normalizedX = (pos.x / canvasW).coerceIn(0f, 1f)
                                                    val normalizedY = (pos.y / canvasH).coerceIn(0f, 1f)
                                                    currentStrokePoints = currentStrokePoints + StrokePoint(normalizedX, normalizedY)
                                                }
                                                EditTool.ERASER -> {
                                                    eraseStrokesNear(
                                                        touchX = pos.x / canvasW,
                                                        touchY = pos.y / canvasH,
                                                        strokes = strokes
                                                    )
                                                }
                                                EditTool.TEXT -> {
                                                    // Dragging in text mode handled per item
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (currentTool == EditTool.PEN && currentStrokePoints.size >= 2) {
                                                strokes.add(
                                                    DrawnStroke(
                                                        points = currentStrokePoints,
                                                        color = selectedColor,
                                                        strokeWidthDp = strokeWidthDp
                                                    )
                                                )
                                            }
                                            currentStrokePoints = emptyList()
                                        },
                                        onDragCancel = {
                                            currentStrokePoints = emptyList()
                                        }
                                    )
                                }
                        ) {
                            // Draw all committed strokes
                            for (stroke in strokes) {
                                if (stroke.points.size >= 2) {
                                    val path = Path()
                                    path.moveTo(stroke.points[0].x * canvasW, stroke.points[0].y * canvasH)
                                    for (i in 1 until stroke.points.size) {
                                        path.lineTo(stroke.points[i].x * canvasW, stroke.points[i].y * canvasH)
                                    }
                                    drawPath(
                                        path = path,
                                        color = stroke.color,
                                        style = Stroke(
                                            width = stroke.strokeWidthDp.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Draw active in-progress stroke
                            if (currentStrokePoints.size >= 2) {
                                val activePath = Path()
                                activePath.moveTo(currentStrokePoints[0].x * canvasW, currentStrokePoints[0].y * canvasH)
                                for (i in 1 until currentStrokePoints.size) {
                                    activePath.lineTo(currentStrokePoints[i].x * canvasW, currentStrokePoints[i].y * canvasH)
                                }
                                drawPath(
                                    path = activePath,
                                    color = selectedColor,
                                    style = Stroke(
                                        width = strokeWidthDp.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Text Overlays on top of the image
                        for (item in textOverlays) {
                            val posX = (item.xPercent * canvasW).coerceIn(0f, canvasW - 50f)
                            val posY = (item.yPercent * canvasH).coerceIn(0f, canvasH - 30f)

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x99000000))
                                    .border(1.dp, if (selectedTextId == item.id) Color(0xFF00E5FF) else Color(0x44FFFFFF), RoundedCornerShape(6.dp))
                                    .pointerInput(item.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            selectedTextId = item.id
                                            val newX = ((item.xPercent * canvasW) + dragAmount.x) / canvasW
                                            val newY = ((item.yPercent * canvasH) + dragAmount.y) / canvasH
                                            item.xPercent = newX.coerceIn(0.02f, 0.95f)
                                            item.yPercent = newY.coerceIn(0.02f, 0.95f)
                                        }
                                    }
                                    .clickable {
                                        selectedTextId = item.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.text,
                                        color = item.color,
                                        fontSize = item.fontSizeSp.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selectedTextId == item.id) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete text",
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    textOverlays.remove(item)
                                                    selectedTextId = null
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Tools Dock
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF131B2E),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tool-specific sub-options
                        when (currentTool) {
                            EditTool.PEN -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Color palette
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        penColors.forEach { c ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .border(
                                                        width = if (selectedColor == c) 2.5.dp else 1.dp,
                                                        color = if (selectedColor == c) Color.White else Color(0x44FFFFFF),
                                                        shape = CircleShape
                                                    )
                                                    .clickable { selectedColor = c }
                                            )
                                        }
                                    }

                                    // Stroke width pills
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(3f to "S", 6f to "M", 12f to "L").forEach { (width, label) ->
                                            Surface(
                                                onClick = { strokeWidthDp = width },
                                                shape = RoundedCornerShape(percent = 50),
                                                color = if (strokeWidthDp == width) Color(0xFF00E5FF) else Color(0x28FFFFFF),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        color = if (strokeWidthDp == width) Color(0xFF090D16) else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            EditTool.ERASER -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Drag finger over drawings to erase",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )

                                    TextButton(
                                        onClick = { strokes.clear() }
                                    ) {
                                        Text(
                                            text = "Clear Drawings",
                                            color = Color(0xFFFF6B6B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            EditTool.TEXT -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Add text annotation to image",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )

                                    Button(
                                        onClick = {
                                            textInput = ""
                                            showAddTextDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        shape = RoundedCornerShape(percent = 50),
                                        modifier = Modifier.testTag("btn_add_text_overlay")
                                    ) {
                                        Text(
                                            text = "+ Add Text",
                                            color = Color(0xFF090D16),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Tool Selector Tabs
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color(0x28FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Pen
                                ToolTabButton(
                                    icon = Icons.Default.Edit,
                                    label = "Pen",
                                    isSelected = currentTool == EditTool.PEN,
                                    onClick = { currentTool = EditTool.PEN }
                                )

                                // Eraser
                                ToolTabButton(
                                    icon = Icons.Default.AutoFixHigh,
                                    label = "Eraser",
                                    isSelected = currentTool == EditTool.ERASER,
                                    onClick = { currentTool = EditTool.ERASER }
                                )

                                // Text Box
                                ToolTabButton(
                                    icon = Icons.Default.TextFields,
                                    label = "Text Box",
                                    isSelected = currentTool == EditTool.TEXT,
                                    onClick = { currentTool = EditTool.TEXT }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to type custom text
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = {
                Text("Add Text Overlay", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Enter custom text…", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Text color choices
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        penColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selectedColor == c) 2.5.dp else 1.dp,
                                        color = if (selectedColor == c) Color.White else Color(0x44FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            textOverlays.add(
                                TextOverlayItem(
                                    text = textInput,
                                    xPercent = 0.35f,
                                    yPercent = 0.45f,
                                    color = selectedColor,
                                    fontSizeSp = 22f
                                )
                            )
                        }
                        showAddTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Add", color = Color(0xFF090D16), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun ToolTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF090D16) else Color.White,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                color = if (isSelected) Color(0xFF090D16) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Erases strokes whose points lie within touch proximity.
 */
private fun eraseStrokesNear(touchX: Float, touchY: Float, strokes: MutableList<DrawnStroke>) {
    val eraseRadius = 0.05f // 5% of canvas dimension
    strokes.removeAll { stroke ->
        stroke.points.any { pt ->
            hypot(pt.x - touchX, pt.y - touchY) < eraseRadius
        }
    }
}

/**
 * Renders source bitmap combined with all user drawn strokes and text overlays
 * into a single high-quality edited output bitmap.
 */
private fun renderEditedBitmap(
    sourceBitmap: Bitmap,
    strokes: List<DrawnStroke>,
    textOverlays: List<TextOverlayItem>
): Bitmap {
    val result = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(result)
    val width = result.width.toFloat()
    val height = result.height.toFloat()

    // 1. Draw strokes
    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    for (stroke in strokes) {
        if (stroke.points.size >= 2) {
            strokePaint.color = stroke.color.toArgb()
            strokePaint.strokeWidth = stroke.strokeWidthDp * (width / 360f).coerceAtLeast(1.5f)

            val path = AndroidPath()
            path.moveTo(stroke.points[0].x * width, stroke.points[0].y * height)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * width, stroke.points[i].y * height)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    // 2. Draw text overlays
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(170, 0, 0, 0)
        style = AndroidPaint.Style.FILL
    }

    for (item in textOverlays) {
        val textSizePx = item.fontSizeSp * (width / 360f).coerceAtLeast(1.2f)
        textPaint.textSize = textSizePx
        textPaint.color = item.color.toArgb()

        val textX = item.xPercent * width
        val textY = item.yPercent * height
        val textWidth = textPaint.measureText(item.text)
        val textHeight = textSizePx

        // Background chip behind text for legibility
        val padding = 8f * (width / 360f).coerceAtLeast(1f)
        val rectF = android.graphics.RectF(
            textX - padding,
            textY - textHeight - padding / 2f,
            textX + textWidth + padding,
            textY + padding / 2f
        )
        canvas.drawRoundRect(rectF, padding, padding, bgPaint)
        canvas.drawText(item.text, textX, textY, textPaint)
    }

    return result
}
