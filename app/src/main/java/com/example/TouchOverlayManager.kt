package com.example

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager

/**
 * TouchOverlayManager
 *
 * Manages the overlay view lifecycle and WindowManager.LayoutParams touch flags.
 *
 * Touch-Pass-Through Configuration:
 * Default configuration includes FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE:
 *   flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
 *           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
 *           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
 *
 * When 3-finger overlay is disabled (the default setting, where Volume Down Long-Press
 * is active), the overlay is completely removed from WindowManager (or configured with
 * FLAG_NOT_TOUCHABLE), ensuring standard phone usage (apps, typing, scrolling) is
 * 100% unimpeded.
 */
class TouchOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "TouchOverlayManager"
    }

    var windowManager: WindowManager? = null
        private set
    var overlayView: ThreeFingerTouchOverlayView? = null
        private set
    private var isOverlayAttached = false
    private var onTriggerAction: (() -> Unit)? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    fun setTriggerCallback(callback: () -> Unit) {
        onTriggerAction = callback
        overlayView?.onThreeFingerSwipeTriggered = callback
    }

    /**
     * Builds default LayoutParams with FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE
     * by default to prevent blocking OS touches.
     */
    fun createDefaultLayoutParams(isTouchable: Boolean = false): WindowManager.LayoutParams {
        val flags = if (isTouchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val width = if (isTouchable) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            WindowManager.LayoutParams.MATCH_PARENT
        }

        val height = if (isTouchable) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            WindowManager.LayoutParams.MATCH_PARENT
        }

        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = if (isTouchable) (120 * context.resources.displayMetrics.density).toInt() else 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Updates overlay attachment and touch flags based on whether 3-finger gesture is enabled.
     * When disabled, removes the overlay from WindowManager, guaranteeing 100% untouched OS touches.
     */
    fun updateOverlayState(isThreeFingerEnabled: Boolean) {
        if (isThreeFingerEnabled) {
            attachOverlay()
        } else {
            detachOverlay()
        }
    }

    /**
     * Attaches the touch overlay view to WindowManager.
     */
    fun attachOverlay() {
        if (isOverlayAttached) return

        val wm = windowManager ?: return
        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.touch_listener_overlay, null) as ThreeFingerTouchOverlayView
            view.onThreeFingerSwipeTriggered = onTriggerAction
            view.enableFloatingPillMode(wm)

            // Use non-blocking FLAG_NOT_TOUCH_MODAL layout flags with WRAP_CONTENT
            val layoutParams = createDefaultLayoutParams(isTouchable = true)

            wm.addView(view, layoutParams)
            overlayView = view
            isOverlayAttached = true
            Log.d(TAG, "Touch overlay attached with TYPE_ACCESSIBILITY_OVERLAY (FLAG_NOT_TOUCH_MODAL floating pill active)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach touch overlay view", e)
        }
    }

    /**
     * Sets FLAG_NOT_TOUCHABLE on the overlay to immediately route all touches to the OS.
     */
    fun setTouchPassThrough(passThrough: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        if (!isOverlayAttached) return

        try {
            val layoutParams = view.layoutParams as? WindowManager.LayoutParams ?: return
            if (passThrough) {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            wm.updateViewLayout(view, layoutParams)
            Log.d(TAG, "Updated overlay layout params: passThrough=$passThrough")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay touch pass-through flags", e)
        }
    }

    /**
     * Detaches and removes the overlay view from WindowManager, guaranteeing zero touch interference.
     */
    fun detachOverlay() {
        if (!isOverlayAttached) return

        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            // First apply FLAG_NOT_TOUCHABLE as safety measure before removing
            setTouchPassThrough(true)
            wm.removeView(view)
            Log.d(TAG, "Touch overlay detached and removed from WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove touch overlay view from WindowManager", e)
        } finally {
            overlayView = null
            isOverlayAttached = false
        }
    }

    /**
     * Toggles visibility of the overlay view (e.g. hides it during screenshot capture and when crop overlay is active).
     */
    fun setOverlayVisibility(visible: Boolean) {
        val view = overlayView
        if (view != null) {
            view.post {
                view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
                Log.d(TAG, "Overlay visibility set to: $visible")
            }
        } else if (visible) {
            attachOverlay()
        }
    }

    /**
     * Ensures the overlay view is properly attached to WindowManager and visible.
     * Re-creates the attachment if the previous view was detached or lost due to configuration/system events.
     */
    fun ensureAttachedAndVisible() {
        val wm = windowManager ?: return
        val currentView = overlayView
        if (!isOverlayAttached || currentView == null || !currentView.isAttachedToWindow) {
            detachOverlay()
            attachOverlay()
        } else {
            currentView.post {
                if (currentView.visibility != android.view.View.VISIBLE) {
                    currentView.visibility = android.view.View.VISIBLE
                    Log.d(TAG, "Restored overlay visibility to VISIBLE")
                }
            }
        }
    }

    /**
     * Emergency safety kill-switch:
     * Directly invokes windowManager.removeView(overlayView) immediately to unblock the screen.
     */
    fun forceRemoveOverlay() {
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
                Log.w(TAG, "Emergency kill-switch: successfully invoked windowManager.removeView(overlayView)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to removeView in emergency kill-switch", e)
            }
        }
        overlayView = null
        isOverlayAttached = false
    }

    fun isAttached(): Boolean = isOverlayAttached
}
