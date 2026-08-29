package com.example

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchOverlayManagerTest {

    private lateinit var context: Context
    private lateinit var overlayManager: TouchOverlayManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        overlayManager = TouchOverlayManager(context)
    }

    @Test
    fun `default layout params include FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE`() {
        val defaultParams = overlayManager.createDefaultLayoutParams(isTouchable = false)

        val hasNotTouchable = (defaultParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        val hasNotFocusable = (defaultParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
        val hasLayoutInScreen = (defaultParams.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0

        assertTrue("Default layout params must have FLAG_NOT_TOUCHABLE to pass touches to OS", hasNotTouchable)
        assertTrue("Default layout params must have FLAG_NOT_FOCUSABLE", hasNotFocusable)
        assertTrue("Default layout params must have FLAG_LAYOUT_IN_SCREEN", hasLayoutInScreen)
    }

    @Test
    fun `touchable layout params omit FLAG_NOT_TOUCHABLE when 3-finger is active`() {
        val touchableParams = overlayManager.createDefaultLayoutParams(isTouchable = true)

        val hasNotTouchable = (touchableParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        val hasNotFocusable = (touchableParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
        val hasNotTouchModal = (touchableParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0

        assertFalse("Touchable layout params must NOT have FLAG_NOT_TOUCHABLE", hasNotTouchable)
        assertTrue("Touchable layout params must still have FLAG_NOT_FOCUSABLE", hasNotFocusable)
        assertTrue("Touchable layout params must have FLAG_NOT_TOUCH_MODAL to allow screen touches to pass through", hasNotTouchModal)
        assertEquals("Touchable layout width must be WRAP_CONTENT to avoid blocking screen", WindowManager.LayoutParams.WRAP_CONTENT, touchableParams.width)
        assertEquals("Touchable layout height must be WRAP_CONTENT to avoid blocking screen", WindowManager.LayoutParams.WRAP_CONTENT, touchableParams.height)
        assertEquals("Touchable layout gravity must be TOP | START for top-left edge docking", android.view.Gravity.TOP or android.view.Gravity.START, touchableParams.gravity)
        assertEquals("Touchable layout x must be 0 (docked to left edge)", 0, touchableParams.x)
        assertTrue("Touchable layout y must be positioned at top edge", touchableParams.y > 0)
    }

    @Test
    fun `overlay state updates appropriately for enabled and disabled states`() {
        assertFalse("Overlay should initially not be attached", overlayManager.isAttached())

        // Disabled by default (Volume Down mode)
        overlayManager.updateOverlayState(false)
        assertFalse("Overlay should remain unattached when 3-finger is disabled", overlayManager.isAttached())

        // Enabled (3-finger mode)
        overlayManager.updateOverlayState(true)
        assertTrue("Overlay should be attached when 3-finger is enabled", overlayManager.isAttached())

        // Disable again
        overlayManager.updateOverlayState(false)
        assertFalse("Overlay should be detached when 3-finger is disabled again", overlayManager.isAttached())
    }

    @Test
    fun `forceRemoveOverlay immediately detaches and clears overlay view`() {
        overlayManager.attachOverlay()
        assertTrue("Overlay should be attached", overlayManager.isAttached())

        overlayManager.forceRemoveOverlay()
        assertFalse("Overlay should be detached after forceRemoveOverlay", overlayManager.isAttached())
        org.junit.Assert.assertNull("overlayView should be null after forceRemoveOverlay", overlayManager.overlayView)
    }

    @Test
    fun `setOverlayVisibility changes visibility without throwing`() {
        overlayManager.attachOverlay()
        assertTrue("Overlay should be attached", overlayManager.isAttached())

        overlayManager.setOverlayVisibility(false)
        overlayManager.setOverlayVisibility(true)
    }
}
