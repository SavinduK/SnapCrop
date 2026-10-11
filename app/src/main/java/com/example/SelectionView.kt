package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * SelectionView
 *
 * Fully interactive cropping view that renders a full-screen screenshot,
 * provides rich touch-based bounding box creation (reselection), fine-grained
 * resizing via 4 corner handles and 4 edge handles, full-box translation (move),
 * and translucent dark masking (#80000000) outside the active selection.
 */
open class SelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class TouchMode {
        NONE,
        CREATE_NEW,
        MOVE,
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM,
        RESIZE_LEFT,
        RESIZE_RIGHT
    }

    var screenshotBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Callback invoked whenever the selection bounding box changes or user interaction ends/starts.
     * @param rect The current bounding box in view coordinates, or null if no valid selection.
     * @param isInteracting True while a drag, move, or resize gesture is actively underway.
     */
    var onSelectionChanged: ((rect: RectF?, isInteracting: Boolean) -> Unit)? = null

    /**
     * When false, touch events on SelectionView are ignored (e.g. during OCR text selection mode).
     */
    var isTouchEnabled: Boolean = true

    /**
     * When true, hides rule-of-thirds and edge handles to display text highlights cleanly.
     */
    var isOcrMode: Boolean = false
        set(value) {
            field = value
            isTouchEnabled = !value
            invalidate()
        }

    // Touch interaction state
    private var touchMode = TouchMode.NONE
    private var hasSelection = false
    private val selectionRect = RectF()

    private var startX = 0f
    private var startY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Reusable rects
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val edgePillRect = RectF()

    // Configuration dimensions (scaled to display density)
    private val minBoxSize by lazy { dpToPx(36f) }
    private val cornerTouchRadius by lazy { dpToPx(32f) }
    private val edgeTouchMargin by lazy { dpToPx(24f) }

    // Paints
    private val maskPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Translucent dark mask
        style = Paint.Style.FILL
    }

    // Google Circle to Search signature multicolor glow palette
    private val googleGlowColors = intArrayOf(
        Color.parseColor("#4285F4"), // Google Blue
        Color.parseColor("#EA4335"), // Google Red
        Color.parseColor("#FBBC05"), // Google Yellow
        Color.parseColor("#34A853"), // Google Green
        Color.parseColor("#4285F4")  // Loop back to Google Blue
    )
    private val googleGlowPositions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    // Outer soft aura glow
    private val glowPaintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(14f)
        alpha = 65
    }

    // Mid vibrant aura glow
    private val glowPaintMid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(7.5f)
        alpha = 135
    }

    // Inner sharp aura glow
    private val glowPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3.6f)
        alpha = 200
    }

    // Thin white box border (Circle to Search signature crisp border)
    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.8f)
    }

    // Corner handle grip dots
    private val cornerHandleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.2f)
    }

    private val cornerHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Edge handle pills (subtle frosted white)
    private val edgePillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 230
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    fun getSelectionRect(): RectF? {
        return if (hasSelection && selectionRect.width() >= minBoxSize && selectionRect.height() >= minBoxSize) {
            RectF(selectionRect)
        } else {
            null
        }
    }

    fun setSelection(rect: RectF) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val left = rect.left.coerceIn(0f, viewW)
        val top = rect.top.coerceIn(0f, viewH)
        val right = rect.right.coerceIn(left, viewW)
        val bottom = rect.bottom.coerceIn(top, viewH)

        selectionRect.set(left, top, right, bottom)
        hasSelection = (right - left >= minBoxSize && bottom - top >= minBoxSize)
        invalidate()
        onSelectionChanged?.invoke(if (hasSelection) selectionRect else null, false)
    }

    /**
     * Selects the entire screen area.
     */
    fun selectAll() {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW > 0f && viewH > 0f) {
            selectionRect.set(0f, 0f, viewW, viewH)
            hasSelection = true
            invalidate()
            onSelectionChanged?.invoke(RectF(selectionRect), false)
        } else {
            post {
                val postW = width.toFloat()
                val postH = height.toFloat()
                if (postW > 0f && postH > 0f) {
                    selectionRect.set(0f, 0f, postW, postH)
                    hasSelection = true
                    invalidate()
                    onSelectionChanged?.invoke(RectF(selectionRect), false)
                }
            }
        }
    }

    /**
     * Resets / clears current crop box, returning the view to initial state.
     */
    fun resetSelection() {
        hasSelection = false
        selectionRect.setEmpty()
        touchMode = TouchMode.NONE
        invalidate()
        onSelectionChanged?.invoke(null, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // 1. Draw full-screen screenshot bitmap
        screenshotBitmap?.let { bmp ->
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(0, 0, width, height)
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
        }

        // 2. Dim area outside active selection with translucent dark mask (#80000000)
        if (hasSelection && selectionRect.width() > 5f && selectionRect.height() > 5f) {
            // Outside dimmed regions
            canvas.drawRect(0f, 0f, viewW, selectionRect.top, maskPaint) // Top
            canvas.drawRect(0f, selectionRect.bottom, viewW, viewH, maskPaint) // Bottom
            canvas.drawRect(0f, selectionRect.top, selectionRect.left, selectionRect.bottom, maskPaint) // Left
            canvas.drawRect(selectionRect.right, selectionRect.top, viewW, selectionRect.bottom, maskPaint) // Right

            // 3. Google Circle to Search signature multicolor glow around the selection
            val cx = selectionRect.centerX()
            val cy = selectionRect.centerY()
            val sweepShader = SweepGradient(cx, cy, googleGlowColors, googleGlowPositions)
            glowPaintOuter.shader = sweepShader
            glowPaintMid.shader = sweepShader
            glowPaintInner.shader = sweepShader

            val cornerRadius = dpToPx(7f)

            // Multi-layer ambient multicolor glow
            canvas.drawRoundRect(selectionRect, cornerRadius, cornerRadius, glowPaintOuter)
            canvas.drawRoundRect(selectionRect, cornerRadius, cornerRadius, glowPaintMid)
            canvas.drawRoundRect(selectionRect, cornerRadius, cornerRadius, glowPaintInner)

            // 4. Thin crisp white box border
            canvas.drawRoundRect(selectionRect, cornerRadius, cornerRadius, whiteBorderPaint)

            // 5. Interactive handles (hidden during OCR mode)
            if (!isOcrMode) {
                // 4 Corner anchor handles
                val handleRadius = dpToPx(4.5f)
                val corners = listOf(
                    selectionRect.left to selectionRect.top,
                    selectionRect.right to selectionRect.top,
                    selectionRect.left to selectionRect.bottom,
                    selectionRect.right to selectionRect.bottom
                )
                for ((hx, hy) in corners) {
                    canvas.drawCircle(hx, hy, handleRadius + dpToPx(1.5f), cornerHandleGlowPaint)
                    canvas.drawCircle(hx, hy, handleRadius, cornerHandleFillPaint)
                }

                // Subtle edge handles
                val pillLen = dpToPx(22f)
                val pillThick = dpToPx(3.5f)
                val pillRadius = pillThick / 2f

                // Top Edge
                edgePillRect.set(
                    cx - pillLen / 2f,
                    selectionRect.top - pillThick / 2f,
                    cx + pillLen / 2f,
                    selectionRect.top + pillThick / 2f
                )
                canvas.drawRoundRect(edgePillRect, pillRadius, pillRadius, edgePillFillPaint)

                // Bottom Edge
                edgePillRect.set(
                    cx - pillLen / 2f,
                    selectionRect.bottom - pillThick / 2f,
                    cx + pillLen / 2f,
                    selectionRect.bottom + pillThick / 2f
                )
                canvas.drawRoundRect(edgePillRect, pillRadius, pillRadius, edgePillFillPaint)

                // Left Edge
                edgePillRect.set(
                    selectionRect.left - pillThick / 2f,
                    cy - pillLen / 2f,
                    selectionRect.left + pillThick / 2f,
                    cy + pillLen / 2f
                )
                canvas.drawRoundRect(edgePillRect, pillRadius, pillRadius, edgePillFillPaint)

                // Right Edge
                edgePillRect.set(
                    selectionRect.right - pillThick / 2f,
                    cy - pillLen / 2f,
                    selectionRect.right + pillThick / 2f,
                    cy + pillLen / 2f
                )
                canvas.drawRoundRect(edgePillRect, pillRadius, pillRadius, edgePillFillPaint)
            }
        } else {
            // When no selection exists, render full-screen translucent dark mask
            canvas.drawRect(0f, 0f, viewW, viewH, maskPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isTouchEnabled) return false
        val x = event.x
        val y = event.y
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y

                if (hasSelection && selectionRect.width() >= minBoxSize && selectionRect.height() >= minBoxSize) {
                    // Check Corner Handles first (precision radius)
                    touchMode = when {
                        hypot(x - selectionRect.left, y - selectionRect.top) <= cornerTouchRadius -> TouchMode.RESIZE_TOP_LEFT
                        hypot(x - selectionRect.right, y - selectionRect.top) <= cornerTouchRadius -> TouchMode.RESIZE_TOP_RIGHT
                        hypot(x - selectionRect.left, y - selectionRect.bottom) <= cornerTouchRadius -> TouchMode.RESIZE_BOTTOM_LEFT
                        hypot(x - selectionRect.right, y - selectionRect.bottom) <= cornerTouchRadius -> TouchMode.RESIZE_BOTTOM_RIGHT

                        // Check Edge Handles
                        abs(y - selectionRect.top) <= edgeTouchMargin && x in (selectionRect.left - edgeTouchMargin)..(selectionRect.right + edgeTouchMargin) -> TouchMode.RESIZE_TOP
                        abs(y - selectionRect.bottom) <= edgeTouchMargin && x in (selectionRect.left - edgeTouchMargin)..(selectionRect.right + edgeTouchMargin) -> TouchMode.RESIZE_BOTTOM
                        abs(x - selectionRect.left) <= edgeTouchMargin && y in (selectionRect.top - edgeTouchMargin)..(selectionRect.bottom + edgeTouchMargin) -> TouchMode.RESIZE_LEFT
                        abs(x - selectionRect.right) <= edgeTouchMargin && y in (selectionRect.top - edgeTouchMargin)..(selectionRect.bottom + edgeTouchMargin) -> TouchMode.RESIZE_RIGHT

                        // Check Inside Box (Move)
                        selectionRect.contains(x, y) -> TouchMode.MOVE

                        // Outside Box -> Tap and drag anywhere to draw a new bounding box (Reselect)
                        else -> {
                            startX = x.coerceIn(0f, viewW)
                            startY = y.coerceIn(0f, viewH)
                            selectionRect.set(startX, startY, startX, startY)
                            TouchMode.CREATE_NEW
                        }
                    }
                } else {
                    // No prior selection, start drawing new bounding box
                    startX = x.coerceIn(0f, viewW)
                    startY = y.coerceIn(0f, viewH)
                    selectionRect.set(startX, startY, startX, startY)
                    touchMode = TouchMode.CREATE_NEW
                }

                if (touchMode == TouchMode.CREATE_NEW) {
                    hasSelection = true
                }

                invalidate()
                onSelectionChanged?.invoke(if (hasSelection && selectionRect.width() > 10f) selectionRect else null, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val curX = x.coerceIn(0f, viewW)
                val curY = y.coerceIn(0f, viewH)

                when (touchMode) {
                    TouchMode.CREATE_NEW -> {
                        val left = min(startX, curX)
                        val top = min(startY, curY)
                        val right = max(startX, curX)
                        val bottom = max(startY, curY)
                        selectionRect.set(left, top, right, bottom)
                    }

                    TouchMode.MOVE -> {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        val boxW = selectionRect.width()
                        val boxH = selectionRect.height()

                        var newLeft = selectionRect.left + dx
                        var newTop = selectionRect.top + dy

                        // Constrain strictly within view bounds
                        if (newLeft < 0f) newLeft = 0f
                        if (newLeft + boxW > viewW) newLeft = viewW - boxW
                        if (newTop < 0f) newTop = 0f
                        if (newTop + boxH > viewH) newTop = viewH - boxH

                        selectionRect.set(newLeft, newTop, newLeft + boxW, newTop + boxH)
                    }

                    TouchMode.RESIZE_TOP_LEFT -> {
                        selectionRect.left = min(curX, selectionRect.right - minBoxSize).coerceAtLeast(0f)
                        selectionRect.top = min(curY, selectionRect.bottom - minBoxSize).coerceAtLeast(0f)
                    }

                    TouchMode.RESIZE_TOP_RIGHT -> {
                        selectionRect.right = max(curX, selectionRect.left + minBoxSize).coerceAtMost(viewW)
                        selectionRect.top = min(curY, selectionRect.bottom - minBoxSize).coerceAtLeast(0f)
                    }

                    TouchMode.RESIZE_BOTTOM_LEFT -> {
                        selectionRect.left = min(curX, selectionRect.right - minBoxSize).coerceAtLeast(0f)
                        selectionRect.bottom = max(curY, selectionRect.top + minBoxSize).coerceAtMost(viewH)
                    }

                    TouchMode.RESIZE_BOTTOM_RIGHT -> {
                        selectionRect.right = max(curX, selectionRect.left + minBoxSize).coerceAtMost(viewW)
                        selectionRect.bottom = max(curY, selectionRect.top + minBoxSize).coerceAtMost(viewH)
                    }

                    TouchMode.RESIZE_TOP -> {
                        selectionRect.top = min(curY, selectionRect.bottom - minBoxSize).coerceAtLeast(0f)
                    }

                    TouchMode.RESIZE_BOTTOM -> {
                        selectionRect.bottom = max(curY, selectionRect.top + minBoxSize).coerceAtMost(viewH)
                    }

                    TouchMode.RESIZE_LEFT -> {
                        selectionRect.left = min(curX, selectionRect.right - minBoxSize).coerceAtLeast(0f)
                    }

                    TouchMode.RESIZE_RIGHT -> {
                        selectionRect.right = max(curX, selectionRect.left + minBoxSize).coerceAtMost(viewW)
                    }

                    TouchMode.NONE -> { /* No-op */ }
                }

                lastTouchX = x
                lastTouchY = y

                invalidate()
                onSelectionChanged?.invoke(if (hasSelection) selectionRect else null, true)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (touchMode == TouchMode.CREATE_NEW) {
                    if (selectionRect.width() < minBoxSize || selectionRect.height() < minBoxSize) {
                        // Accidental tap without meaningful drag
                        resetSelection()
                    } else {
                        hasSelection = true
                    }
                }
                touchMode = TouchMode.NONE
                invalidate()
                onSelectionChanged?.invoke(if (hasSelection) selectionRect else null, false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
                invalidate()
                onSelectionChanged?.invoke(if (hasSelection) selectionRect else null, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
