package com.example

import android.app.Activity
import android.app.SearchManager
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
import android.net.Uri
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
                    onGeminiRequested = { rect ->
                        handleCropAndShareToGemini(rect)
                    },
                    onOcrRequested = { rect, onTextExtracted, onError, onEmpty ->
                        performTextRecognition(rect, onTextExtracted, onEmpty, onError)
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
                    },
                    onSearchGoogle = { text ->
                        handleSearchGoogle(text)
                    },
                    onCopyText = { text ->
                        handleCopyText(text)
                    },
                    onShareText = { text ->
                        handleShareText(text)
                    }
                )
            }
        }
    }

    override fun finish() {
        KeyCaptureService.setOverlayVisible(true)
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        KeyCaptureService.setOverlayVisible(true)
    }

    override fun onStop() {
        super.onStop()
        KeyCaptureService.setOverlayVisible(true)
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
     * Crops the selection and sends it directly to the Gemini app if installed,
     * or opens the Google Play Store page for Gemini if not installed.
     */
    private fun handleCropAndShareToGemini(rect: RectF) {
        val croppedBitmap = extractCroppedBitmap(rect) ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val croppedFile = File(imagesDir, "gemini_crop_${System.currentTimeMillis()}.png")
            FileOutputStream(croppedFile).use { outStream ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            croppedBitmap.recycle()

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                croppedFile
            )

            val geminiPackage = "com.google.android.apps.bard"
            val googleSearchPackage = "com.google.android.googlequicksearchbox"

            // Explicitly grant URI read permissions to target packages
            try {
                grantUriPermission(geminiPackage, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "Could not pre-grant URI permission to $geminiPackage", e)
            }
            try {
                grantUriPermission(googleSearchPackage, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "Could not pre-grant URI permission to $googleSearchPackage", e)
            }

            var launched = false

            // Strategy 1: Direct ACTION_SEND targeting Gemini standalone app
            val geminiSendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setDataAndType(contentUri, "image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(contentResolver, "Gemini Screenshot", contentUri)
                setPackage(geminiPackage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (geminiSendIntent.resolveActivity(packageManager) != null ||
                packageManager.queryIntentActivities(geminiSendIntent, 0).isNotEmpty()
            ) {
                try {
                    startActivity(geminiSendIntent)
                    Toast.makeText(this, getString(R.string.toast_sending_to_gemini), Toast.LENGTH_SHORT).show()
                    launched = true
                } catch (e: Exception) {
                    Log.d(TAG, "Error starting geminiSendIntent", e)
                }
            }

            // Strategy 2: Launch Intent for Gemini App
            if (!launched) {
                val geminiLaunchIntent = packageManager.getLaunchIntentForPackage(geminiPackage)?.apply {
                    action = Intent.ACTION_SEND
                    type = "image/png"
                    setDataAndType(contentUri, "image/png")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    clipData = ClipData.newUri(contentResolver, "Gemini Screenshot", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (geminiLaunchIntent != null) {
                    try {
                        startActivity(geminiLaunchIntent)
                        Toast.makeText(this, getString(R.string.toast_sending_to_gemini), Toast.LENGTH_SHORT).show()
                        launched = true
                    } catch (e: Exception) {
                        Log.d(TAG, "Error starting geminiLaunchIntent", e)
                    }
                }
            }

            // Strategy 3: Try starting without resolve check in case queries was filtered
            if (!launched) {
                try {
                    startActivity(geminiSendIntent)
                    Toast.makeText(this, getString(R.string.toast_sending_to_gemini), Toast.LENGTH_SHORT).show()
                    launched = true
                } catch (e: Exception) {
                    Log.d(TAG, "Direct launch without check failed: ${e.message}")
                }
            }

            // Strategy 4: If standalone Gemini package is not available, check Google Search App Gemini
            if (!launched) {
                val googleSendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    setDataAndType(contentUri, "image/png")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    clipData = ClipData.newUri(contentResolver, "Gemini Screenshot", contentUri)
                    setPackage(googleSearchPackage)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (googleSendIntent.resolveActivity(packageManager) != null ||
                    packageManager.queryIntentActivities(googleSendIntent, 0).isNotEmpty()
                ) {
                    try {
                        startActivity(googleSendIntent)
                        Toast.makeText(this, getString(R.string.toast_sending_to_gemini), Toast.LENGTH_SHORT).show()
                        launched = true
                    } catch (e: Exception) {
                        Log.d(TAG, "Google app send start failed: ${e.message}")
                    }
                }
            }

            // Strategy 5: If not installed, notify user and open Gemini web or Play Store
            if (!launched) {
                Toast.makeText(this, getString(R.string.toast_gemini_not_installed), Toast.LENGTH_LONG).show()
                try {
                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$geminiPackage")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(playStoreIntent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(webIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image with Gemini app", e)
            Toast.makeText(this, "Error opening Gemini app", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    /**
     * Crops the selection and shares it via the native Android Share Sheet with rich image thumbnail preview.
     */
    private fun handleCropAndShare(rect: RectF) {
        val croppedBitmap = extractCroppedBitmap(rect) ?: run {
            Toast.makeText(this, "Screenshot bitmap unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val croppedFile = File(imagesDir, "share_crop_${System.currentTimeMillis()}.png")
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

            // Setup share intent with ClipData and URI permissions to display rich preview on Android 10+
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setDataAndType(contentUri, "image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(contentResolver, getString(R.string.share_cropped_title), contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, getString(R.string.share_cropped_title)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_TITLE, getString(R.string.share_cropped_title))
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

    /**
     * Performs on-device text recognition (OCR) on the cropped region using Google ML Kit.
     */
    private fun performTextRecognition(
        rect: RectF,
        onSuccess: (String, List<String>) -> Unit,
        onEmpty: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val croppedBitmap = extractCroppedBitmap(rect)
        if (croppedBitmap == null) {
            onError(IllegalStateException("Cropped bitmap unavailable"))
            return
        }

        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    val blocks = visionText.textBlocks.mapNotNull { block ->
                        block.text.trim().takeIf { it.isNotEmpty() }
                    }
                    croppedBitmap.recycle()
                    if (text.isEmpty()) {
                        onEmpty()
                    } else {
                        onSuccess(text, blocks)
                    }
                }
                .addOnFailureListener { ex ->
                    croppedBitmap.recycle()
                    Log.e(TAG, "OCR Recognition failed", ex)
                    onError(ex)
                }
        } catch (e: Exception) {
            croppedBitmap.recycle()
            Log.e(TAG, "Error initializing OCR recognizer", e)
            onError(e)
        }
    }

    /**
     * Sends the extracted text directly to Google Web Search.
     */
    private fun handleSearchGoogle(query: String) {
        if (query.isBlank()) return
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(searchIntent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
        } finally {
            finish()
        }
    }

    /**
     * Copies recognized text to the system clipboard.
     */
    private fun handleCopyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SnapCrop OCR", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.toast_text_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * Shares recognized text via the Android Share sheet.
     */
    private fun handleShareText(text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val chooser = Intent.createChooser(sendIntent, getString(R.string.ocr_btn_share_text)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share text", Toast.LENGTH_SHORT).show()
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
 * floating Material3 dock with Reset, Select Text (OCR), Ask Gemini, Copy to Clipboard, Share, and Save to Gallery buttons.
 */
@Composable
private fun CropOverlayContent(
    screenshot: Bitmap?,
    onViewAttached: (SelectionView) -> Unit,
    onGeminiRequested: (RectF) -> Unit,
    onOcrRequested: (
        rect: RectF,
        onSuccess: (String, List<String>) -> Unit,
        onError: (Exception) -> Unit,
        onEmpty: () -> Unit
    ) -> Unit,
    onShareRequested: (RectF) -> Unit,
    onCopyRequested: (RectF) -> Unit,
    onSaveRequested: (RectF) -> Unit,
    onCancelRequested: () -> Unit,
    onSearchGoogle: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit
) {
    val context = LocalContext.current
    var selectionRect by remember { mutableStateOf<RectF?>(null) }
    var isInteracting by remember { mutableStateOf(false) }
    var activeSelectionView by remember { mutableStateOf<SelectionView?>(null) }

    // OCR State
    var isOcrProcessing by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var ocrBlocks by remember { mutableStateOf<List<String>>(emptyList()) }

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

        // 3. Floating Action Dock (Reset, Select Text, Gemini, Copy, Share, Save)
        // Positioned contextually below (or above) the bounding box
        val currentRect = selectionRect
        val hasValidSelection = currentRect != null && currentRect.width() >= 36f && currentRect.height() >= 36f

        if (hasValidSelection && currentRect != null && ocrResultText == null) {
            val marginPx = with(density) { 14.dp.toPx() }
            val toolbarHeightPx = with(density) { 60.dp.toPx() }
            val toolbarWidthPx = with(density) { 340.dp.toPx() }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // OCR In-progress loading indicator
                    if (isOcrProcessing) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xF20F172A),
                            border = BorderStroke(1.dp, Color(0x6600E5FF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(
                                    text = stringResource(R.string.ocr_extracting),
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Main Action Capsule Dock
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0xF21E232B), // Dark capsule dock matching user reference
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.2.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.testTag("floating_action_toolbar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                            // 2. Select Text (OCR) Button
                            CropActionCircleButton(
                                onClick = {
                                    isOcrProcessing = true
                                    onOcrRequested(
                                        currentRect,
                                        { text, blocks ->
                                            isOcrProcessing = false
                                            ocrResultText = text
                                            ocrBlocks = blocks
                                        },
                                        { ex ->
                                            isOcrProcessing = false
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_ocr_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        {
                                            isOcrProcessing = false
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_no_text_detected),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                },
                                contentDescription = stringResource(R.string.crop_btn_select_text),
                                testTag = "btn_select_text_crop",
                                backgroundColor = Color(0x3300E5FF)
                            ) {
                                SelectTextIcon(tint = Color(0xFF00E5FF))
                            }

                            // 3. Ask Gemini Button
                            CropActionCircleButton(
                                onClick = {
                                    onGeminiRequested(currentRect)
                                },
                                contentDescription = stringResource(R.string.crop_btn_gemini),
                                testTag = "btn_gemini_crop"
                            ) {
                                GeminiSparkleIcon()
                            }

                            // 4. Copy to Clipboard Button
                            CropActionCircleButton(
                                onClick = {
                                    onCopyRequested(currentRect)
                                },
                                contentDescription = stringResource(R.string.crop_btn_copy),
                                testTag = "btn_copy_crop"
                            ) {
                                CopyIcon(tint = Color.White)
                            }

                            // 5. Share Button
                            CropActionCircleButton(
                                onClick = {
                                    onShareRequested(currentRect)
                                },
                                contentDescription = stringResource(R.string.crop_btn_share),
                                testTag = "btn_share_crop"
                            ) {
                                ShareNodesIcon(tint = Color.White)
                            }

                            // 6. Save to Gallery Button
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

        // 4. OCR Result Bottom Sheet Modal
        AnimatedVisibility(
            visible = ocrResultText != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ocrResultText?.let { initialText ->
                OcrResultSheet(
                    text = initialText,
                    blocks = ocrBlocks,
                    onDismiss = {
                        ocrResultText = null
                        ocrBlocks = emptyList()
                    },
                    onCopyText = { textToCopy ->
                        onCopyText(textToCopy)
                    },
                    onSearchGoogle = { query ->
                        onSearchGoogle(query)
                    },
                    onShareText = { textToShare ->
                        onShareText(textToShare)
                    }
                )
            }
        }
    }
}

/**
 * OCR Result Sheet: Displays extracted text with interactive options
 * to Copy, Search on Google, and Share.
 */
@Composable
private fun OcrResultSheet(
    text: String,
    blocks: List<String>,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
    onSearchGoogle: (String) -> Unit,
    onShareText: (String) -> Unit
) {
    var editableText by remember(text) { mutableStateOf(text) }
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ocr_result_card"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xF5131926),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color(0x3300E5FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x4494A3B8))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2200E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.ocr_modal_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val wordCount = editableText.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                        Text(
                            text = "$wordCount words • ${editableText.length} characters",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x33334155))
                        .testTag("btn_ocr_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.crop_btn_close),
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Content Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0x33475569)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 220.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = editableText,
                            color = Color(0xFFF1F5F9),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Paragraph / Block chips (if multiple detected)
            if (blocks.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    blocks.take(3).forEachIndexed { index, block ->
                        val snippet = block.take(24) + if (block.length > 24) "…" else ""
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (editableText == block) Color(0x3300E5FF) else Color(0x22334155),
                            border = BorderStroke(
                                1.dp,
                                if (editableText == block) Color(0xFF00E5FF) else Color(0x22475569)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editableText = block }
                        ) {
                            Text(
                                text = snippet,
                                color = if (editableText == block) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Copy, Google Search, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Copy Text Button
                Button(
                    onClick = {
                        onCopyText(editableText)
                        isCopied = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("btn_ocr_copy"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCopied) Color(0xFF10B981) else Color(0xFF00E5FF),
                        contentColor = if (isCopied) Color.White else Color(0xFF0A0F1D)
                    )
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied!" else stringResource(R.string.ocr_btn_copy_all),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // 2. Google Search Button
                Button(
                    onClick = {
                        onSearchGoogle(editableText)
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(46.dp)
                        .testTag("btn_ocr_search_google"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ocr_btn_google_search),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // 3. Share Button
                OutlinedButton(
                    onClick = {
                        onShareText(editableText)
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("btn_ocr_share"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x4464748B)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE2E8F0)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.ocr_btn_share_text),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom vector canvas for Select Text / OCR icon:
 * Four bounding corner brackets with a centered uppercase 'T'.
 */
@Composable
private fun SelectTextIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    ComposeCanvas(modifier = modifier) {
        val strokeW = 1.8f * density
        val w = size.width
        val h = size.height
        val bracketLen = w * 0.24f
        val pad = w * 0.08f

        // Top-left bracket
        val tlPath = Path().apply {
            moveTo(pad, pad + bracketLen)
            lineTo(pad, pad)
            lineTo(pad + bracketLen, pad)
        }
        drawPath(tlPath, tint, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Top-right bracket
        val trPath = Path().apply {
            moveTo(w - pad - bracketLen, pad)
            lineTo(w - pad, pad)
            lineTo(w - pad, pad + bracketLen)
        }
        drawPath(trPath, tint, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-left bracket
        val blPath = Path().apply {
            moveTo(pad, h - pad - bracketLen)
            lineTo(pad, h - pad)
            lineTo(pad + bracketLen, h - pad)
        }
        drawPath(blPath, tint, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-right bracket
        val brPath = Path().apply {
            moveTo(w - pad - bracketLen, h - pad)
            lineTo(w - pad, h - pad)
            lineTo(w - pad, h - pad - bracketLen)
        }
        drawPath(brPath, tint, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Stylized "T" in center
        val cx = w / 2f
        val topY = h * 0.28f
        val botY = h * 0.72f
        val barW = w * 0.40f

        // Top bar of T
        drawLine(
            color = tint,
            start = Offset(cx - barW / 2f, topY),
            end = Offset(cx + barW / 2f, topY),
            strokeWidth = 2.2f * density,
            cap = StrokeCap.Round
        )
        // Vertical stem of T
        drawLine(
            color = tint,
            start = Offset(cx, topY),
            end = Offset(cx, botY),
            strokeWidth = 2.2f * density,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Custom vector canvas for Share to Gemini icon:
 * Distinctive 4-pointed sparkle star with curved concave rays.
 */
@Composable
private fun GeminiSparkleIcon(
    modifier: Modifier = Modifier.size(22.dp)
) {
    ComposeCanvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.44f
        val innerR = size.width * 0.12f

        // Primary 4-pointed sparkle star with curved concave arcs
        val starPath = Path().apply {
            moveTo(cx, cy - r)
            quadraticBezierTo(cx, cy - innerR, cx + r, cy)
            quadraticBezierTo(cx + innerR, cy, cx, cy + r)
            quadraticBezierTo(cx, cy + innerR, cx - r, cy)
            quadraticBezierTo(cx - innerR, cy, cx, cy - r)
            close()
        }

        drawPath(
            path = starPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFFF472B6)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )

        // Accent sparkle star
        val smX = size.width * 0.78f
        val smY = size.height * 0.22f
        val smR = size.width * 0.16f
        val smInnerR = size.width * 0.04f
        val smStarPath = Path().apply {
            moveTo(smX, smY - smR)
            quadraticBezierTo(smX, smY - smInnerR, smX + smR, smY)
            quadraticBezierTo(smX + smInnerR, smY, smX, smY + smR)
            quadraticBezierTo(smX, smY + smInnerR, smX - smR, smY)
            quadraticBezierTo(smX - smInnerR, smY, smX, smY - smR)
            close()
        }
        drawPath(
            path = smStarPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF67E8F9), Color(0xFFC084FC)),
                start = Offset(smX - smR, smY - smR),
                end = Offset(smX + smR, smY + smR)
            )
        )
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
    backgroundColor: Color = Color(0x28FFFFFF),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(backgroundColor)
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
