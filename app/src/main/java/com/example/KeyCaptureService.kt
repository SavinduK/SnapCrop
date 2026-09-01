package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * KeyCaptureService
 *
 * Background AccessibilityService that listens for Volume Down long-press events.
 * Suppresses system volume dialogs when held for 700ms+, captures the screen silently
 * via takeScreenshot (Android 11+ / API 30+), converts the hardware buffer to a software bitmap,
 * stores it temporarily, and launches the CropOverlayActivity.
 */
class KeyCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyCaptureService"
        const val LONG_PRESS_THRESHOLD_MS = 700L
        const val TRIPLE_PRESS_TIMEOUT_MS = 1000L
        const val TEMP_SCREENSHOT_FILE = "temp_screen.png"

        @Volatile
        var instance: KeyCaptureService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        /**
         * Manual trigger used for in-app testing or simulated testing.
         */
        fun triggerScreenshot(context: Context): Boolean {
            val service = instance
            if (service != null) {
                service.performScreenCapture()
                return true
            }
            return false
        }

        /**
         * Updates the trigger mode and immediately refreshes the running service overlay state.
         */
        fun updateTriggerMode(context: Context, mode: TriggerPreferenceManager.TriggerMode) {
            TriggerPreferenceManager.setTriggerMode(context, mode)
            instance?.refreshTriggerConfiguration()
        }

        /**
         * Temporarily shows or hides the floating touch overlay (e.g. while CropOverlayActivity is active).
         */
        fun setOverlayVisible(visible: Boolean) {
            instance?.setOverlayVisibility(visible)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val volumeUpPressTimestamps = mutableListOf<Long>()
    private var isKillSwitchTriggered = false
    private var isOverlayHiddenByCropActivity = false

    private var touchOverlayManager: TouchOverlayManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "KeyCaptureService connected")

        // Ensure key event filtering is enabled programmatically as well
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        setupTouchOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Keep the floating slider attached and visible across app changes and system UI transitions
        if (!isOverlayHiddenByCropActivity && TriggerPreferenceManager.isThreeFingerEnabled(this)) {
            ensureOverlayActive()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed, ensuring overlay is attached and properly positioned")
        mainHandler.postDelayed({
            if (!isOverlayHiddenByCropActivity && TriggerPreferenceManager.isThreeFingerEnabled(this)) {
                ensureOverlayActive()
            }
        }, 120L)
    }

    override fun onInterrupt() {
        Log.w(TAG, "KeyCaptureService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) {
            instance = null
        }
        touchOverlayManager?.detachOverlay()
        touchOverlayManager = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        touchOverlayManager?.detachOverlay()
        touchOverlayManager = null
        super.onDestroy()
    }

    /**
     * Intercepts key events to detect:
     * Triple-press on KEYCODE_VOLUME_UP (3 rapid taps) to trigger the screenshot-crop workflow.
     */
    public override fun onKeyEvent(event: KeyEvent): Boolean {
        // Triple-press on Volume Up to trigger instant screenshot crop
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = SystemClock.uptimeMillis()
                volumeUpPressTimestamps.add(now)
                // Retain only presses within the 1000ms window
                volumeUpPressTimestamps.removeAll { now - it > TRIPLE_PRESS_TIMEOUT_MS }
                if (volumeUpPressTimestamps.size >= 3) {
                    volumeUpPressTimestamps.clear()
                    isKillSwitchTriggered = true
                    Log.d(TAG, "Triple-tap Volume Up detected! Triggering screenshot capture.")
                    vibrateFeedback()
                    performScreenCapture()
                    return true // Consume 3rd press so system volume does not change
                }
            } else if (event.action == KeyEvent.ACTION_UP && isKillSwitchTriggered) {
                isKillSwitchTriggered = false
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Toggles visibility of the floating touch overlay (e.g. while CropOverlayActivity is active or taking screenshot).
     */
    fun setOverlayVisibility(visible: Boolean) {
        isOverlayHiddenByCropActivity = !visible
        mainHandler.post {
            if (!visible) {
                touchOverlayManager?.setOverlayVisibility(false)
            } else {
                if (TriggerPreferenceManager.isThreeFingerEnabled(this)) {
                    ensureOverlayActive()
                }
            }
        }
    }

    /**
     * Ensures the floating overlay is attached and active.
     */
    fun ensureOverlayActive() {
        if (isOverlayHiddenByCropActivity) return
        mainHandler.post {
            if (touchOverlayManager == null) {
                setupTouchOverlay()
            } else {
                touchOverlayManager?.ensureAttachedAndVisible()
            }
        }
    }

    /**
     * Performs silent screenshot capture without MediaProjection prompts using Android 11+ API.
     */
    fun performScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Initiating takeScreenshot on Display.DEFAULT_DISPLAY")

            // Hide the floating dock/overlay immediately so it never appears in the screenshot
            touchOverlayManager?.setOverlayVisibility(false)

            // Post with slight delay to ensure WindowManager hides the view before capturing
            mainHandler.postDelayed({
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            Log.d(TAG, "takeScreenshot succeeded")
                            processScreenshot(screenshotResult)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                            // Restore overlay visibility if screenshot fails
                            setOverlayVisibility(true)
                        }
                    }
                )
            }, 60L)
        } else {
            Log.e(TAG, "takeScreenshot API requires Android 11+ (API 30+)")
        }
    }

    /**
     * Converts hardware buffer bitmap into software bitmap memory, stores directly into
     * ScreenshotHolder for zero-latency instant rendering, caches to disk in background,
     * and launches CropOverlayActivity.
     */
    private fun processScreenshot(screenshotResult: ScreenshotResult) {
        try {
            val hardwareBuffer = screenshotResult.hardwareBuffer
            val colorSpace = screenshotResult.colorSpace
            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            hardwareBuffer.close()

            if (hwBitmap == null) {
                Log.e(TAG, "Hardware buffer wrapped bitmap is null")
                return
            }

            // Convert hardware bitmap into software bitmap (ARGB_8888)
            val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hwBitmap.recycle()

            // 1. Immediately store in memory so CropOverlayActivity renders with 0ms delay
            ScreenshotHolder.bitmap = softwareBitmap

            // 2. Persist to cache file in background thread for sharing / fallback
            Thread {
                try {
                    val tempFile = File(cacheDir, TEMP_SCREENSHOT_FILE)
                    FileOutputStream(tempFile).use { outStream ->
                        softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to asynchronously save screenshot to disk", e)
                }
            }.start()

            Log.d(TAG, "Screenshot ready in memory, launching CropOverlayActivity immediately")
            launchOverlayActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process screenshot", e)
        }
    }

    private fun launchOverlayActivity() {
        val intent = Intent(this, CropOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        }
        startActivity(intent)
    }

    /**
     * Subtle haptic feedback when screenshot capture triggers.
     */
    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration feedback unavailable: ${e.message}")
        }
    }

    /**
     * Initializes TouchOverlayManager to manage the 3-finger touch overlay.
     * When 3-finger detection is disabled (the default setting where Volume Down is used),
     * the overlay is completely detached from WindowManager with FLAG_NOT_TOUCHABLE,
     * ensuring 100% untouched OS screen touch pass-through.
     */
    private fun setupTouchOverlay() {
        if (touchOverlayManager == null) {
            touchOverlayManager = TouchOverlayManager(this).apply {
                setTriggerCallback {
                    Log.d(TAG, "3-finger downward swipe trigger detected via TouchOverlayManager")
                    vibrateFeedback()
                    performScreenCapture()
                }
            }
        }
        refreshTriggerConfiguration()
    }

    /**
     * Refreshes the active trigger configuration.
     * When 3-finger detection is disabled, the overlay is removed from WindowManager
     * (or configured with FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE), ensuring standard
     * OS touches (scrolling, typing, buttons) are completely unimpeded.
     */
    fun refreshTriggerConfiguration() {
        val threeFingerEnabled = TriggerPreferenceManager.isThreeFingerEnabled(this)
        val volumeDownEnabled = TriggerPreferenceManager.isVolumeDownEnabled(this)
        touchOverlayManager?.updateOverlayState(threeFingerEnabled)
        Log.d(TAG, "Refreshed trigger configuration: volumeDownEnabled=$volumeDownEnabled, threeFingerEnabled=$threeFingerEnabled")
    }
}
