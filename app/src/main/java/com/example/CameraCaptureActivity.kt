package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDarkBg
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Full-screen Camera Capture & Interactive Crop Activity.
 *
 * Supports two launch workflows:
 * - [MODE_OVERLAY]: Opened from the Crop Overlay camera icon. Captured image can be cropped,
 *   saved to Saved Clips or Device Gallery, copied to clipboard, or sent to Google Search.
 * - [MODE_SAVED_CLIPS]: Opened from the Saved Clips top-right camera icon. Captures an image,
 *   lets the user crop if necessary, and saves directly into Saved Clips.
 */
class CameraCaptureActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CameraCaptureActivity"
        const val EXTRA_LAUNCH_MODE = "extra_camera_launch_mode"
        const val MODE_OVERLAY = "mode_overlay"
        const val MODE_SAVED_CLIPS = "mode_saved_clips"

        /** Optional test hook allowing JVM Robolectric tests to supply a pre-captured bitmap. */
        internal var testInitialCapturedBitmap: Bitmap? = null

        fun createIntent(context: Context, mode: String = MODE_OVERLAY): Intent {
            return Intent(context, CameraCaptureActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_MODE, mode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyCaptureService.setOverlayVisible(false)
        enableEdgeToEdge()

        val launchMode = intent?.getStringExtra(EXTRA_LAUNCH_MODE) ?: MODE_OVERLAY
        val seededBitmap = testInitialCapturedBitmap
        testInitialCapturedBitmap = null

        setContent {
            MyApplicationTheme {
                CameraCaptureScreen(
                    launchMode = launchMode,
                    initialCapturedBitmap = seededBitmap,
                    onClose = { finish() },
                    onSavedClipComplete = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }

    override fun finish() {
        KeyCaptureService.setOverlayVisible(true)
        super.finish()
    }

    override fun onDestroy() {
        KeyCaptureService.setOverlayVisible(true)
        super.onDestroy()
    }
}

@Composable
internal fun CameraCaptureScreen(
    launchMode: String,
    initialCapturedBitmap: Bitmap? = null,
    onClose: () -> Unit,
    onSavedClipComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            initialCapturedBitmap != null ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        hasRequestedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && initialCapturedBitmap == null) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var originalCapturedBitmap by remember { mutableStateOf(initialCapturedBitmap) }
    var workingBitmap by remember { mutableStateOf(initialCapturedBitmap) }
    var cropRect by remember { mutableStateOf(NormalizedCropRect.FULL) }

    val activeBitmap = workingBitmap
    if (activeBitmap != null) {
        CapturedPhotoReviewScreen(
            launchMode = launchMode,
            bitmap = activeBitmap,
            hasAppliedPriorCrop = workingBitmap !== originalCapturedBitmap,
            cropRect = cropRect,
            onCropRectChange = { cropRect = it },
            onApplyCrop = {
                val cropped = cropRect.extractFromBitmap(activeBitmap)
                workingBitmap = cropped
                cropRect = NormalizedCropRect.FULL
            },
            onResetCrop = {
                workingBitmap = originalCapturedBitmap
                cropRect = NormalizedCropRect.FULL
            },
            onRetake = {
                originalCapturedBitmap = null
                workingBitmap = null
                cropRect = NormalizedCropRect.FULL
            },
            onClose = onClose,
            onSavedClipComplete = onSavedClipComplete
        )
    } else if (hasCameraPermission) {
        LiveCameraViewfinder(
            launchMode = launchMode,
            onPhotoCaptured = { captured ->
                originalCapturedBitmap = captured
                workingBitmap = captured
                cropRect = NormalizedCropRect.FULL
            },
            onClose = onClose
        )
    } else {
        CameraPermissionGate(
            hasRequestedOnce = hasRequestedOnce,
            onRequestPermission = {
                val activity = context as? Activity
                val shouldShowRationale = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
                } ?: true
                if (!hasRequestedOnce || shouldShowRationale) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            },
            onClose = onClose
        )
    }
}

@Composable
private fun LiveCameraViewfinder(
    launchMode: String,
    onPhotoCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isCapturing by remember { mutableStateOf(false) }
    var canSwitchCamera by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember(flashMode) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
    }

    LaunchedEffect(lensFacing, imageCapture) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val hasBack = runCatching {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                }.getOrDefault(false)
                val hasFront = runCatching {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                }.getOrDefault(false)
                canSwitchCamera = hasBack && hasFront

                val desiredSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val resolvedSelector = when {
                    lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> desiredSelector
                    lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> desiredSelector
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.Builder().build()
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    resolvedSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraCapture", "Failed to bind CameraX use cases", e)
                Toast.makeText(context, "Unable to initialize camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_viewfinder_screen")
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xB30F172A))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .testTag("btn_camera_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.crop_btn_close),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color(0xB30F172A),
                border = BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Text(
                    text = if (launchMode == CameraCaptureActivity.MODE_SAVED_CLIPS) {
                        stringResource(R.string.section_saved_storage_title)
                    } else {
                        stringResource(R.string.camera_title_capture)
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xB30F172A))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .testTag("btn_camera_flash")
                ) {
                    val flashIcon = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        else -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = stringResource(R.string.camera_btn_flash),
                        tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White else CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (canSwitchCamera) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xB30F172A))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .testTag("btn_camera_switch_lens")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(R.string.camera_btn_switch_lens),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Shutter Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp, top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bitmap = decodeImageProxyToBitmap(
                                            image = image,
                                            mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT
                                        )
                                        isCapturing = false
                                        onPhotoCaptured(bitmap)
                                    } catch (e: Exception) {
                                        isCapturing = false
                                        Log.e("CameraCapture", "Failed to decode captured photo", e)
                                        Toast.makeText(
                                            context,
                                            "Failed to process captured photo",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        image.close()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Log.e("CameraCapture", "Photo capture failed", exception)
                                    Toast.makeText(
                                        context,
                                        "Capture failed: ${exception.message ?: "Camera error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                    .padding(6.dp)
                    .testTag("btn_camera_shutter"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isCapturing) CyanPrimary.copy(alpha = 0.6f) else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = SlateDarkBg,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun decodeImageProxyToBitmap(
    image: ImageProxy,
    mirrorHorizontally: Boolean
): Bitmap {
    val rawBitmap = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0 && !mirrorHorizontally) {
        return rawBitmap
    }
    val matrix = Matrix().apply {
        if (rotation != 0) {
            postRotate(rotation.toFloat())
        }
        if (mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    val transformed = Bitmap.createBitmap(
        rawBitmap,
        0,
        0,
        rawBitmap.width,
        rawBitmap.height,
        matrix,
        true
    )
    if (transformed !== rawBitmap) {
        rawBitmap.recycle()
    }
    return transformed
}

@Composable
private fun CapturedPhotoReviewScreen(
    launchMode: String,
    bitmap: Bitmap,
    hasAppliedPriorCrop: Boolean,
    cropRect: NormalizedCropRect,
    onCropRectChange: (NormalizedCropRect) -> Unit,
    onApplyCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    onSavedClipComplete: () -> Unit
) {
    val context = LocalContext.current
    val isGoogleInstalled = remember { GoogleImageSearchHelper.isGoogleSearchInstalled(context) }
    val hasActiveSubCrop = !cropRect.isFullImage()

    fun resolveFinalBitmap(): Bitmap = cropRect.extractFromBitmap(bitmap)

    BackHandler(onBack = onRetake)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg)
            .testTag("camera_review_screen")
    ) {
        // Top Header Bar
        Surface(
            color = SlateSurfaceCard,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onRetake,
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0x24FFFFFF),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_camera_retake")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.camera_btn_retake),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.camera_title_review),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x20FFFFFF))
                        .testTag("btn_camera_review_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.crop_btn_close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Interactive Crop Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF060911))
        ) {
            CameraImageCropper(
                bitmap = bitmap,
                cropRect = cropRect,
                onCropRectChange = onCropRectChange,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle Crop Hint & Apply/Reset Pills
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = !hasActiveSubCrop && !hasAppliedPriorCrop,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0xCC0F172A),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Text(
                            text = stringResource(R.string.camera_crop_hint),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (hasActiveSubCrop) {
                    Button(
                        onClick = onApplyCrop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = SlateDarkBg
                        ),
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_camera_apply_crop")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.camera_btn_apply_crop),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (hasActiveSubCrop || hasAppliedPriorCrop) {
                    OutlinedButton(
                        onClick = onResetCrop,
                        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xCC0F172A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_camera_reset_crop")
                    ) {
                        Text(
                            text = stringResource(R.string.camera_btn_reset_crop),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Bottom Action Dock
        Surface(
            color = SlateSurfaceCard,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (launchMode == CameraCaptureActivity.MODE_SAVED_CLIPS) {
                // Saved Clips camera workflow: capture, crop if necessary, save to clips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val finalBmp = resolveFinalBitmap()
                            val savedFile = BatchCropManager.addCroppedBitmapToBatch(context, finalBmp)
                            if (savedFile != null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_saved_to_clips),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSavedClipComplete()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to save to Saved Clips",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = SlateDarkBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_camera_save_to_clips")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.camera_btn_save_to_clips_primary),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Overlay camera workflow: crop, save to Saved Clips or Device Gallery, copy, or Google Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CameraActionPill(
                        icon = Icons.Default.Collections,
                        label = stringResource(R.string.camera_btn_save_clips),
                        accent = Color(0xFFFBBF24),
                        testTag = "btn_camera_save_clips",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val finalBmp = resolveFinalBitmap()
                            val file = BatchCropManager.addCroppedBitmapToBatch(context, finalBmp)
                            if (file != null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_saved_to_clips),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to save to Saved Clips",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )

                    CameraActionPill(
                        icon = Icons.Default.Download,
                        label = stringResource(R.string.camera_btn_save_gallery),
                        accent = Color(0xFF34D399),
                        testTag = "btn_camera_save_gallery",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val finalBmp = resolveFinalBitmap()
                            ScreenshotExportHelper.saveToGallery(context, finalBmp)
                        }
                    )

                    CameraActionPill(
                        icon = Icons.Default.ContentCopy,
                        label = stringResource(R.string.camera_btn_copy),
                        accent = CyanPrimary,
                        testTag = "btn_camera_copy",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val finalBmp = resolveFinalBitmap()
                            ScreenshotExportHelper.copyToClipboard(context, finalBmp)
                        }
                    )

                    if (isGoogleInstalled) {
                        CameraActionPill(
                            icon = Icons.Default.Search,
                            label = stringResource(R.string.camera_btn_google_search),
                            accent = Color(0xFF60A5FA),
                            testTag = "btn_camera_google_search",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val finalBmp = resolveFinalBitmap()
                                GoogleImageSearchHelper.searchWithImage(
                                    context = context,
                                    bitmap = finalBmp,
                                    openPlayStoreIfMissing = false
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraActionPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CameraPermissionGate(
    hasRequestedOnce: Boolean,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("camera_permission_gate"),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .testTag("btn_permission_close")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.crop_btn_close),
                tint = Color.White
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateSurfaceCard,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.camera_permission_title),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.camera_permission_desc),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = SlateDarkBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_camera_allow_permission")
                ) {
                    Text(
                        text = if (hasRequestedOnce) {
                            stringResource(R.string.camera_btn_open_settings)
                        } else {
                            stringResource(R.string.camera_btn_allow)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
