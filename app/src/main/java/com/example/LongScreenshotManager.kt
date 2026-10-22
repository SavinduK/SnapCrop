package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * LongScreenshotManager
 *
 * Implements the interactive page-by-page scroll-capture mechanism:
 * 1. Takes initial screen capture without any UI interference.
 * 2. Displays a floating scroll-down toolbar ([ ↓ Scroll Down ] and [ Done ✓ ]).
 * 3. On each click to [ Scroll Down ], hides toolbar, scrolls down by one page, captures,
 *    and increments page count, allowing the user to scroll page by page repeatedly.
 * 4. When the user finishes (either after an inactivity delay with no user clicks or by tapping Done),
 *    seamlessly stitches all taken photos together into a single continuous long image without
 *    duplicate, overlapping, or missing sections.
 * 5. Closes the toolbar and presents the top pill overlay with thumbnail, copy, save, share, delete, and close.
 */
object LongScreenshotManager {

    private const val TAG = "LongScreenshotManager"
    private const val AUTO_FINISH_DELAY_MS = 2800L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var scrollToolbarView: View? = null
    private var topPillOverlayView: View? = null

    private val capturedBitmaps = mutableListOf<Bitmap>()
    private var currentStitchedBitmap: Bitmap? = null
    private var isCapturing = false
    private var autoFinishRunnable: Runnable? = null

    /**
     * Initiates the interactive long screenshot workflow using KeyCaptureService.
     */
    fun startCapture(service: KeyCaptureService, initialBitmap: Bitmap? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(service, "Scroll capture requires Android 11+", Toast.LENGTH_SHORT).show()
            return
        }

        cleanup()
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        service.setOverlayVisibility(false) // Hide edge touch overlay immediately

        isCapturing = true
        capturedBitmaps.clear()
        currentStitchedBitmap = null

        if (initialBitmap != null) {
            val swBitmap = initialBitmap.copy(Bitmap.Config.ARGB_8888, false)
            capturedBitmaps.add(swBitmap)
            currentStitchedBitmap = swBitmap
            ScreenshotHolder.bitmap = swBitmap
            ScreenshotHolder.isLongScreenshot = true
            ScreenshotHolder.pageCount = 1

            showScrollToolbar(service)
        } else {
            // Give 320ms for any open CropOverlayActivity to finish and exit the screen
            mainHandler.postDelayed({
                captureInitialPage(service)
            }, 320L)
        }
    }

    private fun captureInitialPage(service: KeyCaptureService) {
        if (!isCapturing) return

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    val hwBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                    hwBuffer.close()

                    val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap?.recycle()

                    if (swBitmap != null) {
                        capturedBitmaps.add(swBitmap)
                        currentStitchedBitmap = swBitmap
                        ScreenshotHolder.bitmap = swBitmap
                        ScreenshotHolder.isLongScreenshot = true
                        ScreenshotHolder.pageCount = 1

                        showScrollToolbar(service)
                    } else {
                        cleanup()
                        service.setOverlayVisibility(true)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Initial screenshot failed: $errorCode")
                    cleanup()
                    service.setOverlayVisibility(true)
                }
            }
        )
    }

    /**
     * Shows the floating scroll toolbar at the bottom ([ ↓ Scroll Down ] & [ Done ✓ ]).
     * Re-enables the scroll button and starts the inactivity auto-finish timer.
     */
    private fun showScrollToolbar(service: KeyCaptureService) {
        if (!isCapturing) return
        val wm = windowManager ?: return

        if (scrollToolbarView == null) {
            val inflater = LayoutInflater.from(service)
            scrollToolbarView = inflater.inflate(R.layout.scroll_capture_toolbar, null)

            val btnScrollDown = scrollToolbarView?.findViewById<LinearLayout>(R.id.btn_scroll_capture_down)
            val btnDone = scrollToolbarView?.findViewById<LinearLayout>(R.id.btn_scroll_capture_done)

            btnScrollDown?.setOnClickListener {
                cancelAutoFinish()
                btnScrollDown.isEnabled = false
                btnScrollDown.alpha = 0.5f
                performScrollStep(service)
            }

            btnDone?.setOnClickListener {
                cancelAutoFinish()
                finishAndShowTopPill(service)
            }
        }

        // Re-enable scroll button for subsequent page click
        val btnScrollDown = scrollToolbarView?.findViewById<LinearLayout>(R.id.btn_scroll_capture_down)
        btnScrollDown?.isEnabled = true
        btnScrollDown?.alpha = 1.0f

        val tvStatus = scrollToolbarView?.findViewById<TextView>(R.id.tv_scroll_capture_status)
        val count = capturedBitmaps.size
        tvStatus?.text = if (count == 1) "Page 1" else "$count pages"

        val density = service.resources.displayMetrics.density
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (56 * density).toInt()
        }

        try {
            if (scrollToolbarView?.parent == null) {
                wm.addView(scrollToolbarView, params)
            } else {
                scrollToolbarView?.visibility = View.VISIBLE
                wm.updateViewLayout(scrollToolbarView, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show scroll toolbar", e)
        }

        // Schedule auto-finish if there's no user click after some time
        scheduleAutoFinish(service)
    }

    private fun scheduleAutoFinish(service: KeyCaptureService) {
        cancelAutoFinish()
        val runnable = Runnable {
            if (isCapturing) {
                Log.d(TAG, "Inactivity timeout reached, auto-stitching long screenshot")
                finishAndShowTopPill(service)
            }
        }
        autoFinishRunnable = runnable
        mainHandler.postDelayed(runnable, AUTO_FINISH_DELAY_MS)
    }

    private fun cancelAutoFinish() {
        autoFinishRunnable?.let { mainHandler.removeCallbacks(it) }
        autoFinishRunnable = null
    }

    /**
     * Performs a single scroll down step:
     * 1. Hides toolbar so it doesn't appear in the capture.
     * 2. Swipes up smoothly to scroll down the next page section.
     * 3. Waits for scroll inertia to settle, then captures the screen.
     */
    private fun performScrollStep(service: KeyCaptureService) {
        cancelAutoFinish()
        scrollToolbarView?.visibility = View.GONE // Ensure 100% hidden during capture

        val dm = service.resources.displayMetrics
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()
        val statusBarH = (28 * dm.density).toInt()
        val navBarH = (24 * dm.density).toInt()

        // Smooth vertical scroll gesture (72% down to 28% height in 360ms)
        val swipePath = Path().apply {
            moveTo(screenW / 2f, screenH * 0.72f)
            lineTo(screenW / 2f, screenH * 0.28f)
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 360)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        mainHandler.postDelayed({
            if (!isCapturing) return@postDelayed
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    // Wait 500ms for inertial scrolling and layout rendering to settle
                    mainHandler.postDelayed({
                        captureScrolledPage(service, statusBarH, navBarH)
                    }, 500L)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll gesture cancelled, finishing session")
                    finishAndShowTopPill(service)
                }
            }, mainHandler)
        }, 50L)
    }

    private fun captureScrolledPage(service: KeyCaptureService, statusBarH: Int, navBarH: Int) {
        if (!isCapturing) return

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    val hwBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                    hwBuffer.close()

                    val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap?.recycle()

                    if (swBitmap != null) {
                        val prevBmp = capturedBitmaps.lastOrNull()
                        if (prevBmp != null && LongScreenshotStitcher.isEssentiallyIdentical(prevBmp, swBitmap, statusBarH, navBarH)) {
                            // Bottom of the content reached (screen didn't move)
                            Toast.makeText(service, "End of page reached", Toast.LENGTH_SHORT).show()
                            finishAndShowTopPill(service)
                        } else {
                            // Successfully captured the next page!
                            capturedBitmaps.add(swBitmap)
                            ScreenshotHolder.pageCount = capturedBitmaps.size

                            // Re-display toolbar so the user can continue scrolling page by page
                            showScrollToolbar(service)
                        }
                    } else {
                        finishAndShowTopPill(service)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Scrolled screenshot failed: $errorCode")
                    finishAndShowTopPill(service)
                }
            }
        )
    }

    /**
     * Finishes scroll capture, stitches the accumulated screenshots seamlessly into one long image,
     * removes the scroll toolbar, and displays the top pill overlay.
     */
    private fun finishAndShowTopPill(service: KeyCaptureService) {
        cancelAutoFinish()
        isCapturing = false

        // Remove scroll toolbar
        val wm = windowManager
        if (wm != null && scrollToolbarView != null) {
            try {
                wm.removeView(scrollToolbarView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing scroll toolbar", e)
            }
            scrollToolbarView = null
        }

        if (capturedBitmaps.isEmpty()) {
            cleanup()
            service.setOverlayVisibility(true)
            return
        }

        val dm = service.resources.displayMetrics
        val statusBarH = (28 * dm.density).toInt()
        val navBarH = (24 * dm.density).toInt()

        // Seamless stitching of all captured pages without overlap or duplicates
        val finalBitmap = if (capturedBitmaps.size > 1) {
            LongScreenshotStitcher.stitch(capturedBitmaps, statusBarH, navBarH)
        } else {
            capturedBitmaps[0]
        }

        currentStitchedBitmap = finalBitmap
        ScreenshotHolder.bitmap = finalBitmap
        ScreenshotHolder.isLongScreenshot = true
        ScreenshotHolder.pageCount = capturedBitmaps.size

        showTopPillOverlay(service, finalBitmap)
    }

    /**
     * Displays the small horizontal pill-shaped overlay on the TOP with:
     * Thumbnail, Copy, Save, Share, Delete, and Close buttons.
     */
    fun showTopPillOverlay(context: Context, bitmap: Bitmap) {
        val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        dismissTopPillOverlay()

        val inflater = LayoutInflater.from(context)
        val pillView = inflater.inflate(R.layout.long_screenshot_top_pill, null)
        topPillOverlayView = pillView

        val ivThumb = pillView.findViewById<ImageView>(R.id.iv_pill_thumbnail)
        val tvPages = pillView.findViewById<TextView>(R.id.tv_pill_page_count)
        val btnCopy = pillView.findViewById<ImageButton>(R.id.btn_pill_copy)
        val btnSave = pillView.findViewById<ImageButton>(R.id.btn_pill_save)
        val btnShare = pillView.findViewById<ImageButton>(R.id.btn_pill_share)
        val btnDelete = pillView.findViewById<ImageButton>(R.id.btn_pill_delete)
        val btnClose = pillView.findViewById<ImageButton>(R.id.btn_pill_close)

        // Generate scaled thumbnail
        try {
            val thumbW = 44
            val thumbH = (44f * bitmap.height / bitmap.width).toInt().coerceIn(44, 76)
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbW, thumbH, true)
            ivThumb.setImageBitmap(thumb)
        } catch (e: Exception) {
            ivThumb.setImageBitmap(bitmap)
        }

        val count = ScreenshotHolder.pageCount.coerceAtLeast(1)
        tvPages.text = if (count == 1) "1 page" else "$count pages"

        btnCopy.setOnClickListener {
            ScreenshotExportHelper.copyToClipboard(context, bitmap)
            dismissTopPillOverlay()
        }

        btnSave.setOnClickListener {
            ScreenshotExportHelper.saveToGallery(context, bitmap)
            dismissTopPillOverlay()
        }

        btnShare.setOnClickListener {
            ScreenshotExportHelper.share(context, bitmap)
            dismissTopPillOverlay()
        }

        btnDelete.setOnClickListener {
            ScreenshotExportHelper.delete(context)
            dismissTopPillOverlay()
        }

        btnClose.setOnClickListener {
            dismissTopPillOverlay()
        }

        val density = context.resources.displayMetrics.density
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val layoutType = if (context is AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (28 * density).toInt()
        }

        try {
            wm.addView(pillView, params)
            Log.d(TAG, "Top pill overlay attached to WindowManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach top pill overlay", e)
        }
    }

    fun dismissTopPillOverlay() {
        val wm = windowManager
        val view = topPillOverlayView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing top pill overlay", e)
            }
        }
        topPillOverlayView = null
        cleanup()
        KeyCaptureService.setOverlayVisible(true)
    }

    fun cleanup() {
        cancelAutoFinish()
        isCapturing = false
        val wm = windowManager
        if (wm != null) {
            scrollToolbarView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
            }
            topPillOverlayView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
            }
        }
        scrollToolbarView = null
        topPillOverlayView = null
        capturedBitmaps.clear()
    }
}
