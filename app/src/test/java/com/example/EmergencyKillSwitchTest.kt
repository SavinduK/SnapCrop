package com.example

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyKillSwitchTest {

    private lateinit var context: Context
    private lateinit var service: KeyCaptureService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset preferences to BOTH for testing kill-switch deactivation
        TriggerPreferenceManager.setTriggerMode(context, TriggerPreferenceManager.TriggerMode.BOTH)
        service = Robolectric.buildService(KeyCaptureService::class.java).create().get()
    }

    private fun createKeyEvent(action: Int, keyCode: Int, eventTime: Long): KeyEvent {
        return KeyEvent(
            0L,
            eventTime,
            action,
            keyCode,
            0,
            0,
            0,
            0,
            KeyEvent.FLAG_FROM_SYSTEM
        )
    }

    @Test
    fun `rapid triple-press on VOLUME_UP triggers emergency kill-switch and resets preference`() {
        assertEquals(TriggerPreferenceManager.TriggerMode.BOTH, TriggerPreferenceManager.getTriggerMode(service))

        val baseTime = SystemClock.uptimeMillis()

        // 1st press
        val event1Down = createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, baseTime)
        val consumed1 = service.onKeyEvent(event1Down)
        assertFalse("First press should not be consumed", consumed1)

        val event1Up = createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 50)
        service.onKeyEvent(event1Up)

        // 2nd press (200ms later)
        val event2Down = createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 200)
        val consumed2 = service.onKeyEvent(event2Down)
        assertFalse("Second press should not be consumed", consumed2)

        val event2Up = createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 250)
        service.onKeyEvent(event2Up)

        // 3rd press (400ms later - well within 1000ms threshold)
        val event3Down = createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 400)
        val consumed3 = service.onKeyEvent(event3Down)
        assertTrue("Third rapid press should be consumed as emergency kill-switch", consumed3)

        // Preference should be reverted to VOLUME_DOWN_ONLY immediately
        assertEquals(
            TriggerPreferenceManager.TriggerMode.VOLUME_DOWN_ONLY,
            TriggerPreferenceManager.getTriggerMode(service)
        )
    }

    @Test
    fun `two presses on VOLUME_UP do not trigger emergency kill-switch`() {
        val baseTime = SystemClock.uptimeMillis()

        // 1st press
        val event1Down = createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, baseTime)
        service.onKeyEvent(event1Down)
        val event1Up = createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 50)
        service.onKeyEvent(event1Up)

        // 2nd press
        val event2Down = createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, baseTime + 150)
        val consumed2 = service.onKeyEvent(event2Down)
        assertFalse("Two presses should not trigger kill switch", consumed2)

        // Preference remains BOTH
        assertEquals(TriggerPreferenceManager.TriggerMode.BOTH, TriggerPreferenceManager.getTriggerMode(service))
    }

    @Test
    fun `screenshotHolder holds and clears bitmap in memory`() {
        assertNull(ScreenshotHolder.bitmap)

        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        ScreenshotHolder.bitmap = testBitmap
        assertNotNull(ScreenshotHolder.bitmap)
        assertEquals(testBitmap, ScreenshotHolder.bitmap)

        ScreenshotHolder.clear()
        assertNull(ScreenshotHolder.bitmap)
        testBitmap.recycle()
    }
}
