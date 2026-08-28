package com.example

import android.app.Activity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
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
 * and a floating Jetpack Compose action toolbar with Reset and Share buttons.
 */
class CropOverlayActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CropOverlayActivity"
    }

    private var loadedBitmap: Bitmap? = null
    private var selectionViewRef: SelectionView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // 2. Disk fallback
        val tempFile = File(cacheDir, KeyCaptureService.TEMP_SCREENSHOT_FILE)
        if (tempFile.exists()) {
            try {
                loadedBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode screenshot bitmap from disk", e)
            }
        }

        if (loadedBitmap == null) {
            loadedBitmap = createFallbackScreenBitmap()
        }
    }

    /**
     * Generates a sample screen bitmap for interactive testing in environments
     * without active accessibility permissions or physical volume keys.
     */
    private fun createFallbackScreenBitmap(): Bitmap {
        val dm = resources.displayMetrics
        val width = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val height = if (dm.heightPixels > 0) dm.heightPixels else 2400

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Slate background
        val bgPaint = Paint().apply {
            color = AndroidColor.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Simulated top bar
        val barPaint = Paint().apply {
            color = AndroidColor.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 130f, barPaint)

        val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#94A3B8")
            textSize = 38f
        }
        canvas.drawText("09:41 • 5G • 100%", 60f, 85f, statusTextPaint)

        // Card Preview
        val cardPaint = Paint().apply {
            color = AndroidColor.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#00E5FF")
            textSize = 52f
            isFakeBoldText = true
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#CBD5E1")
            textSize = 34f
        }

        val cardRect = RectF(60f, 240f, width - 60f, 720f)
        canvas.drawRoundRect(cardRect, 32f, 32f, cardPaint)
        canvas.drawText("SnapCrop Interactive Canvas", 110f, 340f, cyanPaint)
        canvas.drawText("• Drag anywhere to create or reselect a bounding box", 110f, 430f, subTextPaint)
        canvas.drawText("• Drag the circular corner or edge handles to resize", 110f, 510f, subTextPaint)
        canvas.drawText("• Drag inside the box to move it freely", 110f, 590f, subTextPaint)
        canvas.drawText("• Tap the Share button to launch the system share sheet", 110f, 670f, subTextPaint)

        return bitmap
    }

    /**
     * Crops the enclosed bitmap region, writes it to cacheDir/images/selection.png,
     * and opens the native Android Share Sheet via FileProvider.
     */
    private fun handleCropAndShare(rect: RectF) {
        val bitmap = loadedBitmap ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val view = selectionViewRef ?: run {
            finish()
            return
        }

        val viewW = view.width.toFloat()
        val viewH = view.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) {
            finish()
            return
        }

        try {
            val scaleX = bitmap.width.toFloat() / viewW
            val scaleY = bitmap.height.toFloat() / viewH

            val cropLeft = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
            val cropTop = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
            val cropRight = (rect.right * scaleX).toInt().coerceIn(cropLeft + 1, bitmap.width)
            val cropBottom = (rect.bottom * scaleY).toInt().coerceIn(cropTop + 1, bitmap.height)

            val cropWidth = (cropRight - cropLeft).coerceIn(1, bitmap.width - cropLeft)
            val cropHeight = (cropBottom - cropTop).coerceIn(1, bitmap.height - cropTop)

            // Crop the screenshot bitmap
            val croppedBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)

            // Save to cacheDir/images/selection.png
            val imagesDir = File(cacheDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val croppedFile = File(imagesDir, "selection.png")
            FileOutputStream(croppedFile).use { outStream ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            croppedBitmap.recycle()

            Log.d(TAG, "Cropped image successfully saved to ${croppedFile.absolutePath}")

            // Open native Android Share Sheet via FileProvider
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

    override fun onDestroy() {
        ScreenshotHolder.clear()
        loadedBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        loadedBitmap = null
        selectionViewRef = null
        super.onDestroy()
    }
}

/**
 * CropOverlayContent
 *
 * Full-screen Compose layer that coordinates the SelectionView and anchors the
 * floating Material3 toolbar (Reset & Share) with positioning logic.
 */
@Composable
private fun CropOverlayContent(
    screenshot: Bitmap?,
    onViewAttached: (SelectionView) -> Unit,
    onShareRequested: (RectF) -> Unit,
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

        // 3. Floating Action Toolbar (Reset & Share)
        // Positioned contextually below (or above) the bounding box
        val currentRect = selectionRect
        val hasValidSelection = currentRect != null && currentRect.width() >= 36f && currentRect.height() >= 36f

        if (hasValidSelection && currentRect != null) {
            val marginPx = with(density) { 14.dp.toPx() }
            val toolbarHeightPx = with(density) { 54.dp.toPx() }
            val toolbarWidthPx = with(density) { 210.dp.toPx() }
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
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xF20F172A), // Slate 900 at 95% opacity
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.2.dp, Color(0x8000E5FF)),
                    modifier = Modifier.testTag("floating_action_toolbar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reset / Retake Button
                        OutlinedButton(
                            onClick = {
                                activeSelectionView?.resetSelection()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFCBD5E1)
                            ),
                            border = BorderStroke(1.dp, Color(0x4094A3B8)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                            modifier = Modifier.testTag("btn_reset_crop")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.crop_btn_reset),
                                modifier = Modifier.size(17.dp),
                                tint = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.crop_btn_reset),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Share Button
                        Button(
                            onClick = {
                                onShareRequested(currentRect)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF0A0F1D)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            modifier = Modifier.testTag("btn_share_crop")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.crop_btn_share),
                                modifier = Modifier.size(17.dp),
                                tint = Color(0xFF0A0F1D)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.crop_btn_share),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
