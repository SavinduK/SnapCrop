package com.example

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionViewTest {

    private lateinit var context: Context
    private lateinit var selectionView: SelectionView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        selectionView = SelectionView(context)
        // Lay out the view as 1000 x 2000 px
        selectionView.measure(1000, 2000)
        selectionView.layout(0, 0, 1000, 2000)
    }

    private fun sendTouch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        selectionView.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun `initial state has no selection`() {
        assertNull("Initial selection should be null", selectionView.getSelectionRect())
    }

    @Test
    fun `drag on screen creates valid bounding box`() {
        // Drag from (100, 200) to (500, 600)
        sendTouch(MotionEvent.ACTION_DOWN, 100f, 200f)
        sendTouch(MotionEvent.ACTION_MOVE, 500f, 600f)
        sendTouch(MotionEvent.ACTION_UP, 500f, 600f)

        val rect = selectionView.getSelectionRect()
        assertNotNull("Selection should exist after drag", rect)
        assertEquals(100f, rect!!.left, 1f)
        assertEquals(200f, rect.top, 1f)
        assertEquals(500f, rect.right, 1f)
        assertEquals(600f, rect.bottom, 1f)
    }

    @Test
    fun `drag inside box moves entire selection without resizing`() {
        // Initial box: (200, 300, 600, 700), width = 400, height = 400
        selectionView.setSelection(RectF(200f, 300f, 600f, 700f))

        // Tap inside at (400, 500) and drag by (+50, +60)
        sendTouch(MotionEvent.ACTION_DOWN, 400f, 500f)
        sendTouch(MotionEvent.ACTION_MOVE, 450f, 560f)
        sendTouch(MotionEvent.ACTION_UP, 450f, 560f)

        val rect = selectionView.getSelectionRect()
        assertNotNull(rect)
        assertEquals(250f, rect!!.left, 1f)
        assertEquals(360f, rect.top, 1f)
        assertEquals(650f, rect.right, 1f)
        assertEquals(760f, rect.bottom, 1f)
        assertEquals(400f, rect.width(), 1f)
        assertEquals(400f, rect.height(), 1f)
    }

    @Test
    fun `move operation is constrained within view bounds`() {
        // Initial box near right edge
        selectionView.setSelection(RectF(800f, 100f, 1000f, 300f))

        // Drag far beyond right edge (+500px)
        sendTouch(MotionEvent.ACTION_DOWN, 900f, 200f)
        sendTouch(MotionEvent.ACTION_MOVE, 1400f, 200f)
        sendTouch(MotionEvent.ACTION_UP, 1400f, 200f)

        val rect = selectionView.getSelectionRect()
        assertNotNull(rect)
        // Right edge must not exceed view width (1000f)
        assertEquals(1000f, rect!!.right, 1f)
        assertEquals(800f, rect.left, 1f)
        assertEquals(200f, rect.width(), 1f)
    }

    @Test
    fun `drag corner handle resizes selection`() {
        // Initial box: (200, 200, 500, 500)
        selectionView.setSelection(RectF(200f, 200f, 500f, 500f))

        // Drag bottom-right corner from (500, 500) to (700, 800)
        sendTouch(MotionEvent.ACTION_DOWN, 500f, 500f)
        sendTouch(MotionEvent.ACTION_MOVE, 700f, 800f)
        sendTouch(MotionEvent.ACTION_UP, 700f, 800f)

        val rect = selectionView.getSelectionRect()
        assertNotNull(rect)
        assertEquals(200f, rect!!.left, 1f)
        assertEquals(200f, rect.top, 1f)
        assertEquals(700f, rect.right, 1f)
        assertEquals(800f, rect.bottom, 1f)
    }

    @Test
    fun `drag edge handle resizes only that edge`() {
        // Initial box: (200, 200, 600, 600)
        selectionView.setSelection(RectF(200f, 200f, 600f, 600f))

        // Drag top edge handle at centerX=400, top=200 upwards to 100
        sendTouch(MotionEvent.ACTION_DOWN, 400f, 200f)
        sendTouch(MotionEvent.ACTION_MOVE, 400f, 100f)
        sendTouch(MotionEvent.ACTION_UP, 400f, 100f)

        val rect = selectionView.getSelectionRect()
        assertNotNull(rect)
        assertEquals(200f, rect!!.left, 1f)
        assertEquals(100f, rect.top, 1f)
        assertEquals(600f, rect.right, 1f)
        assertEquals(600f, rect.bottom, 1f)
    }

    @Test
    fun `tap and drag outside box initiates reselection`() {
        // Initial box: (200, 200, 400, 400)
        selectionView.setSelection(RectF(200f, 200f, 400f, 400f))

        // Tap far outside at (600, 700) and drag to (900, 950)
        sendTouch(MotionEvent.ACTION_DOWN, 600f, 700f)
        sendTouch(MotionEvent.ACTION_MOVE, 900f, 950f)
        sendTouch(MotionEvent.ACTION_UP, 900f, 950f)

        val rect = selectionView.getSelectionRect()
        assertNotNull(rect)
        assertEquals(600f, rect!!.left, 1f)
        assertEquals(700f, rect.top, 1f)
        assertEquals(900f, rect.right, 1f)
        assertEquals(950f, rect.bottom, 1f)
    }

    @Test
    fun `resetSelection clears crop box`() {
        selectionView.setSelection(RectF(100f, 100f, 400f, 400f))
        assertNotNull(selectionView.getSelectionRect())

        selectionView.resetSelection()
        assertNull(selectionView.getSelectionRect())
    }
}
