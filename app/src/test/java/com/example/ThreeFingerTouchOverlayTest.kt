package com.example

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeFingerTouchOverlayTest {

    private lateinit var context: Context
    private lateinit var overlayView: ThreeFingerTouchOverlayView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        overlayView = ThreeFingerTouchOverlayView(context)
    }

    private fun createMotionEvent(
        action: Int,
        pointerCount: Int,
        yCoords: List<Float>
    ): MotionEvent {
        val properties = Array(pointerCount) { i ->
            MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointerCount) { i ->
            MotionEvent.PointerCoords().apply {
                x = (i * 100f) + 50f
                y = if (i < yCoords.size) yCoords[i] else 100f
            }
        }
        return MotionEvent.obtain(
            0L, 10L, action,
            pointerCount, properties, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    @Test
    fun `single finger touch returns false to pass through to underlying apps`() {
        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 1, listOf(200f))
        val eventMove = createMotionEvent(MotionEvent.ACTION_MOVE, 1, listOf(250f))
        val eventUp = createMotionEvent(MotionEvent.ACTION_UP, 1, listOf(250f))

        assertFalse("1-finger ACTION_DOWN should pass through (return false)", overlayView.dispatchTouchEvent(eventDown))
        assertFalse("1-finger ACTION_MOVE should pass through (return false)", overlayView.dispatchTouchEvent(eventMove))
        assertFalse("1-finger ACTION_UP should pass through (return false)", overlayView.dispatchTouchEvent(eventUp))

        eventDown.recycle()
        eventMove.recycle()
        eventUp.recycle()
    }

    @Test
    fun `two finger touch returns false to pass through to underlying apps`() {
        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 2, listOf(200f, 210f))
        val eventMove = createMotionEvent(MotionEvent.ACTION_MOVE, 2, listOf(300f, 310f))
        val eventUp = createMotionEvent(MotionEvent.ACTION_UP, 2, listOf(300f, 310f))

        assertFalse("2-finger ACTION_DOWN should pass through (return false)", overlayView.dispatchTouchEvent(eventDown))
        assertFalse("2-finger ACTION_MOVE should pass through (return false)", overlayView.dispatchTouchEvent(eventMove))
        assertFalse("2-finger ACTION_UP should pass through (return false)", overlayView.dispatchTouchEvent(eventUp))

        eventDown.recycle()
        eventMove.recycle()
        eventUp.recycle()
    }

    @Test
    fun `three finger gesture is consumed and returns true`() {
        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 3, listOf(100f, 100f, 100f))
        val consumed = overlayView.dispatchTouchEvent(eventDown)

        assertTrue("3-finger touch should be consumed (return true)", consumed)
        eventDown.recycle()
    }

    @Test
    fun `three finger downward swipe greater than 300px triggers screenshot action`() {
        var triggered = false
        overlayView.onThreeFingerSwipeTriggered = {
            triggered = true
        }

        // Start 3 fingers at Y=100
        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 3, listOf(100f, 100f, 100f))
        overlayView.dispatchTouchEvent(eventDown)
        assertFalse("Should not trigger on initial down touch", triggered)

        // Drag 3 fingers down by 150px (Y=250), which is <= 300px
        val eventMoveIntermediate = createMotionEvent(MotionEvent.ACTION_MOVE, 3, listOf(250f, 250f, 250f))
        overlayView.dispatchTouchEvent(eventMoveIntermediate)
        assertFalse("Should not trigger when dragged <= 300px", triggered)

        // Drag 3 fingers down to Y=450 (delta = 350px > 300px threshold)
        val eventMoveTrigger = createMotionEvent(MotionEvent.ACTION_MOVE, 3, listOf(450f, 450f, 450f))
        overlayView.dispatchTouchEvent(eventMoveTrigger)
        assertTrue("Should trigger when dragged downwards > 300px", triggered)

        eventDown.recycle()
        eventMoveIntermediate.recycle()
        eventMoveTrigger.recycle()
    }

    @Test
    fun `three finger upward swipe does not trigger screenshot action`() {
        var triggered = false
        overlayView.onThreeFingerSwipeTriggered = {
            triggered = true
        }

        // Start at Y=500
        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 3, listOf(500f, 500f, 500f))
        overlayView.dispatchTouchEvent(eventDown)

        // Drag upwards to Y=100 (delta = -400px)
        val eventMoveUp = createMotionEvent(MotionEvent.ACTION_MOVE, 3, listOf(100f, 100f, 100f))
        overlayView.dispatchTouchEvent(eventMoveUp)

        assertFalse("Upward swipe should not trigger capture", triggered)

        eventDown.recycle()
        eventMoveUp.recycle()
    }

    @Test
    fun `single tap on floating pill in floating pill mode triggers capture action`() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        overlayView.enableFloatingPillMode(wm)
        overlayView.layoutParams = android.view.WindowManager.LayoutParams()

        var triggered = false
        overlayView.onThreeFingerSwipeTriggered = {
            triggered = true
        }

        val eventDown = createMotionEvent(MotionEvent.ACTION_DOWN, 1, listOf(100f))
        overlayView.dispatchTouchEvent(eventDown)
        assertFalse("Should not trigger immediately on ACTION_DOWN", triggered)

        val eventUp = createMotionEvent(MotionEvent.ACTION_UP, 1, listOf(100f))
        overlayView.dispatchTouchEvent(eventUp)
        assertTrue("Single tap on floating pill should trigger capture action", triggered)

        eventDown.recycle()
        eventUp.recycle()
    }

    @Test
    fun `enableFloatingBarMode sets bar mode and measures edge bar dimensions`() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        overlayView.enableFloatingBarMode(wm)
        assertTrue("Bar mode should be active", overlayView.isFloatingBarMode)

        overlayView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        assertTrue("Measured width should be positive", overlayView.measuredWidth > 0)
        assertTrue("Measured height should be positive", overlayView.measuredHeight > 0)
        assertTrue("Bar should be taller than wide", overlayView.measuredHeight > overlayView.measuredWidth)
    }
}
