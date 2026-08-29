package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CropOverlayActivity
 *
 * Fullscreen transparent activity hosting the interactive SelectionView
 * and a floating Jetpack Compose action toolbar with Reset, Copy to Clipboard,
 * Share, and Save to Gallery buttons.
 */
class CropOverlayActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CropOverlayActivity"
    }

    private var loadedBitmap: Bitmap? = null
    private var selectionViewRef: SelectionView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide floating edge bar / dock while the crop overlay is active
        KeyCaptureService.setOverlayVisible(false)

        // Disable window transitions so the crop overlay screen appears instantly
        // without any transition animation or flicker revealing the app screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Ensure edge-to-edge fullscreen layout
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        loadScreenshotBitmap()

        // Apply loaded bitmap directly to the window background immediately
        // so the window surface displays the frozen screen on the very first frame
        loadedBitmap?.let { bmp ->
            window.setBackgroundDrawable(BitmapDrawable(resources, bmp))
        }

        setContent {
            MaterialTheme {
                CropOverlayContent(
                    screenshot = loadedBitmap,
                    onViewAttached = { view ->
                        selectionViewRef = view
                    },
                    onShareRequested = { rect ->
                        handleCropAndShare(rect)
                    },
                    onCopyRequested = { rect ->
                        handleCropAndCopy(rect)
                    },
                    onSaveRequested = { rect ->
                        handleCropAndSaveToGallery(rect)
                    },
                    onCancelRequested = {
                        finish()
                    }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Loads the screenshot bitmap:
     * 1. Checks the in-memory ScreenshotHolder for zero-latency instant rendering.
     * 2. Falls back to disk cache if memory holder is empty.
     * 3. Uses synthetic preview canvas if no capture exists.
     */
    private fun loadScreenshotBitmap() {
        // 1. Instant in-memory check (0ms delay)
        val inMemoryBitmap = ScreenshotHolder.bitmap
        if (inMemoryBitmap != null && !inMemoryBitmap.isRecycled) {
            loadedBitmap = inMemoryBitmap
            return
        }

        // 2. Disk cache fallback
        val tempFile = File(cacheDir, KeyCaptureService.TEMP_SCREENSHOT_FILE)
        if (tempFile.exists() && tempFile.length() > 0) {
            try {
                loadedBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                if (loadedBitmap != null) {
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode screenshot from disk cache", e)
            }
        }

        // 3. Fallback: generate a synthetic preview placeholder if launched directly
        loadedBitmap = generateFallbackBitmap()
    }

    /**
     * Generates a clean synthetic background bitmap for direct in-app testing.
     */
    private fun generateFallbackBitmap(): Bitmap {
        val dm = resources.displayMetrics
        val width = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val height = if (dm.heightPixels > 0) dm.heightPixels else 2400

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Slate gradient background
        val bgPaint = Paint().apply {
            color = AndroidColor.rgb(15, 23, 42) // Slate 900
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle accent grid lines
        val linePaint = Paint().apply {
            color = AndroidColor.argb(30, 0, 229, 255)
            strokeWidth = 2f
        }
        val step = (60 * dm.density).toInt()
        for (x in 0..width step step) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), linePaint)
        }
        for (y in 0..height step step) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), linePaint)
        }

        // Header text preview
        val textPaint = Paint().apply {
            color = AndroidColor.WHITE
            textSize = 28f * dm.scaledDensity
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("SnapCrop Live Screen Capture", 80f, 400f, textPaint)

        val subTextPaint = Paint().apply {
            color = AndroidColor.rgb(148, 163, 184) // Slate 400
            textSize = 16f * dm.scaledDensity
            isAntiAlias = true
        }
        canvas.drawText("• Drag anywhere across the screen to create a crop box", 80f, 470f, subTextPaint)
        canvas.drawText("• Move inside the box or drag edge handles to resize", 80f, 520f, subTextPaint)
        canvas.drawText("• Copy to clipboard, save to gallery, or share instantly", 80f, 570f, subTextPaint)

        return bitmap
    }

    /**
     * Helper to extract cropped bitmap from current selection.
     */
    private fun extractCroppedBitmap(rect: RectF): Bitmap? {
        val bitmap = loadedBitmap ?: return null
        val view = selectionViewRef ?: return null

        val viewW = view.width.toFloat()
        val viewH = view.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return null

        val scaleX = bitmap.width.toFloat() / viewW
        val scaleY = bitmap.height.toFloat() / viewH

        val cropLeft = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val cropTop = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val cropRight = (rect.right * scaleX).toInt().coerceIn(cropLeft + 1, bitmap.width)
        val cropBottom = (rect.bottom * scaleY).toInt().coerceIn(cropTop + 1, bitmap.height)

        val cropWidth = (cropRight - cropLeft).coerceIn(1, bitmap.width - cropLeft)
        val cropHeight = (cropBottom - cropTop).coerceIn(1, bitmap.height - cropTop)

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }

    /**
     * Crops the selection and shares it via the native Android Share Sheet.
     */
    private fun handleCropAndShare(rect: RectF) {
        val croppedBitmap = extractCroppedBitmap(rect) ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val croppedFile = File(imagesDir, "selection.png")
            FileOutputStream(croppedFile).use { outStream ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            croppedBitmap.recycle()

            Log.d(TAG, "Cropped image saved to ${croppedFile.absolutePath}")

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                croppedFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, getString(R.string.share_cropped_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop or share image", e)
            Toast.makeText(this, "Error sharing cropped image", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    /**
     * Crops the selection and copies it to the device clipboard.
     */
    private fun handleCropAndCopy(rect: RectF) {
        val croppedBitmap = extractCroppedBitmap(rect) ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val croppedFile = File(imagesDir, "clipboard_crop.png")
            FileOutputStream(croppedFile).use { outStream ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            croppedBitmap.recycle()

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                croppedFile
            )

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(contentResolver, "Cropped Screenshot", contentUri)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Cropped image copied to clipboard: $contentUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to clipboard", e)
            Toast.makeText(this, "Error copying to clipboard", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    /**
     * Crops the selection and saves it to the system Gallery (Pictures/SnapCrop).
     */
    private fun handleCropAndSaveToGallery(rect: RectF) {
        val croppedBitmap = extractCroppedBitmap(rect) ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            val filename = "SnapCrop_$timestamp.png"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SnapCrop")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = contentResolver.insert(collectionUri, values)
            if (itemUri != null) {
                contentResolver.openOutputStream(itemUri)?.use { outStream ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(itemUri, values, null, null)
                }

                Toast.makeText(this, getString(R.string.toast_saved_to_gallery), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Cropped image saved to Gallery: $itemUri")
            } else {
                Toast.makeText(this, "Failed to save image to gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to gallery", e)
            Toast.makeText(this, "Error saving to gallery", Toast.LENGTH_SHORT).show()
        } finally {
            croppedBitmap.recycle()
            finish()
        }
    }

    override fun onDestroy() {
        ScreenshotHolder.clear()
        loadedBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        loadedBitmap = null
        selectionViewRef = null

        // Restore floating edge bar / dock visibility when leaving the crop overlay
        KeyCaptureService.setOverlayVisible(true)

        super.onDestroy()
    }
}

/**
 * CropOverlayContent
 *
 * Full-screen Compose layer that coordinates the SelectionView and anchors the
 * floating Material3 dock with Reset, Copy to Clipboard, Share, and Save to Gallery buttons.
 */
@Composable
private fun CropOverlayContent(
    screenshot: Bitmap?,
    onViewAttached: (SelectionView) -> Unit,
    onShareRequested: (RectF) -> Unit,
    onCopyRequested: (RectF) -> Unit,
    onSaveRequested: (RectF) -> Unit,
    onCancelRequested: () -> Unit
) {
    var selectionRect by remember { mutableStateOf<RectF?>(null) }
    var isInteracting by remember { mutableStateOf(false) }
    var activeSelectionView by remember { mutableStateOf<SelectionView?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // 1. Custom Interactive Selection View
        AndroidView(
            factory = { ctx ->
                SelectionView(ctx).apply {
                    screenshotBitmap = screenshot
                    onSelectionChanged = { rect, interacting ->
                        selectionRect = rect?.let { RectF(it) }
                        isInteracting = interacting
                    }
                    onViewAttached(this)
                    activeSelectionView = this
                }
            },
            update = { view ->
                view.screenshotBitmap = screenshot
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top-Bar Utility Header: Cancel Button & Guidance Chip
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            // Contextual Guidance Chip
            val hintText = if (selectionRect == null) {
                stringResource(R.string.crop_hint_initial)
            } else {
                stringResource(R.string.crop_hint_selected)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xD90F172A),
                border = BorderStroke(1.dp, Color(0x6000E5FF)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selectionRect == null) Icons.Default.CropFree else Icons.Default.OpenWith,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = hintText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Cancel / Close (✕) Button
            IconButton(
                onClick = onCancelRequested,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xD90F172A))
                    .testTag("btn_cancel_overlay")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.crop_btn_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. Floating Action Dock (Reset, Copy, Share, Save)
        // Positioned contextually below (or above) the bounding box
        val currentRect = selectionRect
        val hasValidSelection = currentRect != null && currentRect.width() >= 36f && currentRect.height() >= 36f

        if (hasValidSelection && currentRect != null) {
            val marginPx = with(density) { 14.dp.toPx() }
            val toolbarHeightPx = with(density) { 60.dp.toPx() }
            val toolbarWidthPx = with(density) { 240.dp.toPx() }
            val topSafePx = with(density) { 80.dp.toPx() }
            val bottomSafePx = with(density) { 56.dp.toPx() }

            // Vertical positioning: prefer below selection box, fallback to above or inside
            val targetY = if (currentRect.bottom + marginPx + toolbarHeightPx <= screenH - bottomSafePx) {
                currentRect.bottom + marginPx
            } else if (currentRect.top - marginPx - toolbarHeightPx >= topSafePx) {
                currentRect.top - marginPx - toolbarHeightPx
            } else {
                (currentRect.bottom - toolbarHeightPx - marginPx).coerceIn(topSafePx, max(topSafePx, screenH - toolbarHeightPx - bottomSafePx))
            }

            // Horizontal positioning: centered on selection box, clamped within screen margins
            val targetX = (currentRect.centerX() - toolbarWidthPx / 2f).coerceIn(
                marginPx,
                max(marginPx, screenW - toolbarWidthPx - marginPx)
            )

            AnimatedVisibility(
                visible = !isInteracting,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xF21E232B), // Dark capsule dock matching user reference
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.2.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.testTag("floating_action_toolbar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Reset / Retake Button
                        CropActionCircleButton(
                            onClick = {
                                activeSelectionView?.resetSelection()
                            },
                            contentDescription = stringResource(R.string.crop_btn_reset),
                            testTag = "btn_reset_crop"
                        ) {
                            ResetCropIcon(tint = Color.White)
                        }

                        // 2. Copy to Clipboard Button
                        CropActionCircleButton(
                            onClick = {
                                onCopyRequested(currentRect)
                            },
                            contentDescription = stringResource(R.string.crop_btn_copy),
                            testTag = "btn_copy_crop"
                        ) {
                            CopyIcon(tint = Color.White)
                        }

                        // 3. Share Button
                        CropActionCircleButton(
                            onClick = {
                                onShareRequested(currentRect)
                            },
                            contentDescription = stringResource(R.string.crop_btn_share),
                            testTag = "btn_share_crop"
                        ) {
                            ShareNodesIcon(tint = Color.White)
                        }

                        // 4. Save to Gallery Button
                        CropActionCircleButton(
                            onClick = {
                                onSaveRequested(currentRect)
                            },
                            contentDescription = stringResource(R.string.crop_btn_save),
                            testTag = "btn_save_crop"
                        ) {
                            SaveToGalleryIcon(tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular icon button container for the floating dock action items.
 */
@Composable
private fun CropActionCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x28FFFFFF))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Custom vector canvas for Copy to Clipboard icon matching user reference:
 * Two overlapping rounded rectangular cards.
 */
@Composable
private fun CopyIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    ComposeCanvas(modifier = modifier) {
        val strokeW = 2f * density
        val cr = 3.5f * density
        val boxW = size.width * 0.56f
        val boxH = size.height * 0.62f

        // Front rectangle (shifted bottom-left)
        val frontLeft = size.width * 0.12f
        val frontTop = size.height * 0.26f
        drawRoundRect(
            color = tint,
            topLeft = Offset(frontLeft, frontTop),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(cr, cr),
            style = Stroke(width = strokeW)
        )

        // Back rectangle (shifted top-right)
        val backLeft = frontLeft + size.width * 0.20f
        val backTop = size.height * 0.12f
        val backRight = backLeft + boxW
        val backBottom = backTop + boxH

        val backPath = Path().apply {
            moveTo(frontLeft + boxW * 0.40f, backTop)
            lineTo(backRight - cr, backTop)
            quadraticBezierTo(backRight, backTop, backRight, backTop + cr)
            lineTo(backRight, frontTop + boxH * 0.55f)
        }
        drawPath(
            path = backPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Custom vector canvas for Share icon matching user reference:
 * Three filled circular nodes connected with stroke lines.
 */
@Composable
private fun ShareNodesIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    ComposeCanvas(modifier = modifier) {
        val strokeW = 2.2f * density
        val dotRadius = 3f * density

        val leftDot = Offset(size.width * 0.22f, size.height * 0.50f)
        val topRightDot = Offset(size.width * 0.78f, size.height * 0.22f)
        val bottomRightDot = Offset(size.width * 0.78f, size.height * 0.78f)

        // Connecting lines
        drawLine(
            color = tint,
            start = leftDot,
            end = topRightDot,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = leftDot,
            end = bottomRightDot,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Filled node circles
        drawCircle(color = tint, radius = dotRadius, center = leftDot)
        drawCircle(color = tint, radius = dotRadius, center = topRightDot)
        drawCircle(color = tint, radius = dotRadius, center = bottomRightDot)
    }
}

/**
 * Custom vector canvas for Save to Gallery icon matching user reference:
 * Outer rounded square border with centered downward arrow.
 */
@Composable
private fun SaveToGalleryIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    ComposeCanvas(modifier = modifier) {
        val strokeW = 2f * density
        val cr = 4.5f * density
        val pad = size.width * 0.08f
        val boxSize = size.width - 2 * pad

        // Outer rounded square frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(pad, pad),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(cr, cr),
            style = Stroke(width = strokeW)
        )

        // Inner downward arrow
        val cx = size.width / 2f
        val arrowTop = size.height * 0.26f
        val arrowBottom = size.height * 0.72f
        val headW = size.width * 0.20f
        val headH = size.height * 0.16f

        // Stem
        drawLine(
            color = tint,
            start = Offset(cx, arrowTop),
            end = Offset(cx, arrowBottom),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Arrow head (V)
        val arrowHead = Path().apply {
            moveTo(cx - headW, arrowBottom - headH)
            lineTo(cx, arrowBottom)
            lineTo(cx + headW, arrowBottom - headH)
        }
        drawPath(
            path = arrowHead,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Custom vector canvas for Reset crop icon matching the circular dock design:
 * Circular counter-clockwise arc with directional arrow tip.
 */
@Composable
private fun ResetCropIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    ComposeCanvas(modifier = modifier) {
        val strokeW = 2f * density
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.width * 0.32f

        // Circular arc (275 degrees)
        drawArc(
            color = tint,
            startAngle = 45f,
            sweepAngle = 275f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Arrow tip
        val angleRad = Math.toRadians(45.0 + 275.0)
        val tipX = (cx + radius * Math.cos(angleRad)).toFloat()
        val tipY = (cy + radius * Math.sin(angleRad)).toFloat()
        val arrowW = 3.5f * density

        val arrowHead = Path().apply {
            moveTo(tipX - arrowW, tipY - arrowW * 1.5f)
            lineTo(tipX, tipY)
            lineTo(tipX + arrowW * 1.5f, tipY - arrowW * 0.5f)
        }
        drawPath(
            path = arrowHead,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
