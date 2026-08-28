package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

/**
 * ThreeFingerTouchOverlayView
 *
 * Provides a non-blocking on-screen capture trigger.
 * When attached via WindowManager with FLAG_NOT_TOUCH_MODAL and WRAP_CONTENT,
 * it acts as a sleek, white translucent bar docked at the top-left edge of the screen.
 *
 * Touches outside this compact bar pass 100% through to underlying applications,
 * completely preventing any touch blocking on the user's phone.
 *
 * Interacting with the bar:
 * - Single tap on the bar triggers instant screen capture and crop.
 * - Dragging moves the bar vertically along the left screen edge.
 * - Multi-touch gestures (pointerCount == 3) continue to be detected and processed.
 */
class ThreeFingerTouchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), View.OnTouchListener {

    companion object {
        private const val TAG = "ThreeFingerTouchOverlay"
        const val SWIPE_DOWN_THRESHOLD_PX = 300f
    }

    var onThreeFingerSwipeTriggered: (() -> Unit)? = null

    // Multi-touch tracking
    private var startY = 0f
    private var isTracking = false
    private var hasTriggered = false

    // Floating bar configuration
    var isFloatingPillMode = false
        private set
    val isFloatingBarMode: Boolean
        get() = isFloatingPillMode

    private var windowManager: WindowManager? = null

    // Bar drag and tap tracking
    private var downRawX = 0f
    private var downRawY = 0f
    private var initialWindowY = 0
    private var isDraggingPill = false
    private var isPressedState = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Reusable path for the edge-docked rounded bar
    private val barPath = Path()
    private val barRect = RectF()
    private val notchRect = RectF()

    // Paints for rendering the white translucent edge bar
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setOnTouchListener(this)
    }

    /**
     * Enables floating bar mode with WindowManager access for interactive dragging and tapping.
     */
    fun enableFloatingPillMode(wm: WindowManager) {
        this.windowManager = wm
        this.isFloatingPillMode = true
        invalidate()
    }

    fun enableFloatingBarMode(wm: WindowManager) {
        enableFloatingPillMode(wm)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val defaultWidth = (20 * density).toInt()
        val defaultHeight = (80 * density).toInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(defaultWidth, widthSize)
            else -> defaultWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(defaultHeight, heightSize)
            else -> defaultHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isFloatingPillMode) return

        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density

        // Outer shape: docked to left edge (x=0), rounded on right side
        barRect.set(0f, 0f, w, h)
        val cornerRadius = minOf(w, 14f * density)
        val radii = floatArrayOf(
            0f, 0f,                         // Top-left: flat against bezel
            cornerRadius, cornerRadius,     // Top-right: rounded
            cornerRadius, cornerRadius,     // Bottom-right: rounded
            0f, 0f                          // Bottom-left: flat against bezel
        )
        barPath.reset()
        barPath.addRoundRect(barRect, radii, Path.Direction.CW)

        // White translucent background fill
        barBgPaint.color = if (isPressedState) {
            Color.argb(235, 255, 255, 255) // Bright translucent white when pressed
        } else {
            Color.argb(165, 255, 255, 255) // Translucent white resting
        }
        canvas.drawPath(barPath, barBgPaint)

        // Subtle dark hairline border for crisp contrast on light backgrounds
        barBorderPaint.color = if (isPressedState) {
            Color.argb(70, 0, 0, 0)
        } else {
            Color.argb(45, 0, 0, 0)
        }
        barBorderPaint.strokeWidth = 1.2f * density
        canvas.drawPath(barPath, barBorderPaint)

        // Subtle vertical grip indicator notch
        val notchW = 3f * density
        val notchH = 26f * density
        val notchLeft = (w - notchW) / 2f
        val notchTop = (h - notchH) / 2f
        notchRect.set(notchLeft, notchTop, notchLeft + notchW, notchTop + notchH)

        gripPaint.color = if (isPressedState) {
            Color.argb(140, 71, 85, 105) // Slate 600 tone
        } else {
            Color.argb(85, 100, 116, 139) // Subtle slate tone
        }
        canvas.drawRoundRect(notchRect, notchW / 2f, notchW / 2f, gripPaint)
    }

    /**
     * Handles single-finger touches on the floating bar:
     * - Tap: invokes capture action immediately.
     * - Drag: smoothly updates the bar position vertically along the left screen edge.
     */
    private fun handlePillTouchEvent(event: MotionEvent): Boolean {
        val wm = windowManager ?: return false
        val lp = layoutParams as? WindowManager.LayoutParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                initialWindowY = lp.y
                isDraggingPill = false
                isPressedState = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - downRawY
                val deltaX = event.rawX - downRawX
                if (!isDraggingPill && Math.hypot(deltaX.toDouble(), deltaY.toDouble()) > touchSlop) {
                    isDraggingPill = true
                }
                if (isDraggingPill) {
                    val displayMetrics = resources.displayMetrics
                    val minY = (24 * displayMetrics.density).toInt()
                    val maxY = (displayMetrics.heightPixels - height - (24 * displayMetrics.density)).toInt().coerceAtLeast(minY)
                    lp.y = (initialWindowY + deltaY).toInt().coerceIn(minY, maxY)
                    lp.x = 0 // Docked to left edge
                    try {
                        wm.updateViewLayout(this, lp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update bar position on screen", e)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressedState = false
                invalidate()
                if (!isDraggingPill) {
                    Log.d(TAG, "Floating edge bar tapped. Triggering screenshot action.")
                    onThreeFingerSwipeTriggered?.invoke()
                }
                isDraggingPill = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
                isDraggingPill = false
                invalidate()
                return true
            }
        }
        return false
    }

    /**
     * Processes motion events for 3-finger swipe detection:
     * - Returns false immediately for pointerCount < 3 when standalone.
     * - Consumes and processes touch events ONLY when event.pointerCount == 3
     *   AND event.actionMasked is ACTION_POINTER_DOWN or ACTION_MOVE (or ACTION_DOWN in tests).
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        // Return false IMMEDIATELY for any pointerCount < 3
        if (event.pointerCount < 3) {
            if (isTracking) {
                isTracking = false
                hasTriggered = false
            }
            return false
        }

        val action = event.actionMasked

        // Consume and process touch events ONLY when pointerCount == 3 AND action is ACTION_POINTER_DOWN, ACTION_MOVE, or ACTION_DOWN
        if (event.pointerCount == 3 && (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)) {
            when (action) {
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                    startY = calculateAverageY(event)
                    isTracking = true
                    hasTriggered = false
                    Log.d(TAG, "3-finger touch started at centroid Y=$startY")
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTracking) {
                        startY = calculateAverageY(event)
                        isTracking = true
                        hasTriggered = false
                    }
                    val currentY = calculateAverageY(event)
                    val deltaY = currentY - startY

                    if (deltaY > SWIPE_DOWN_THRESHOLD_PX && !hasTriggered) {
                        hasTriggered = true
                        Log.d(TAG, "3-finger downward drag ($deltaY px > ${SWIPE_DOWN_THRESHOLD_PX}px) detected. Triggering capture flow.")
                        onThreeFingerSwipeTriggered?.invoke()
                    }
                }
            }
            // Consumed: 3-finger gesture is handled
            return true
        }

        // Reset tracking on pointer up / cancel and pass through
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isTracking = false
            hasTriggered = false
        }

        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = processTouchEvent(event)
        if (handled) {
            return true
        }
        if (isFloatingPillMode) {
            return handlePillTouchEvent(event)
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 3) {
            return processTouchEvent(event)
        }
        if (isFloatingPillMode) {
            return handlePillTouchEvent(event)
        }
        return processTouchEvent(event)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        return dispatchTouchEvent(event)
    }

    private fun calculateAverageY(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount.coerceAtMost(3)
        for (i in 0 until count) {
            sum += event.getY(i)
        }
        return if (count > 0) sum / count else 0f
    }
}

