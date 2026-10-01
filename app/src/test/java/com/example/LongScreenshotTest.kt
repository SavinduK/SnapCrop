package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongScreenshotTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("snapcrop_trigger_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("snapcrop_feature_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ScreenshotHolder.clear()
    }

    @Test
    fun testDefaultCaptureModeIsStandard() {
        val mode = TriggerPreferenceManager.getCaptureMode(context)
        assertEquals(TriggerPreferenceManager.CaptureMode.STANDARD, mode)
        assertFalse(TriggerPreferenceManager.isLongScreenshotDefault(context))
    }

    @Test
    fun testSettingLongScreenshotCaptureMode() {
        TriggerPreferenceManager.setCaptureMode(context, TriggerPreferenceManager.CaptureMode.LONG_SCREENSHOT)
        val mode = TriggerPreferenceManager.getCaptureMode(context)
        assertEquals(TriggerPreferenceManager.CaptureMode.LONG_SCREENSHOT, mode)
        assertTrue(TriggerPreferenceManager.isLongScreenshotDefault(context))
    }

    @Test
    fun testLongScreenshotFeatureToggle() {
        assertTrue(CropFeaturePreferenceManager.isLongScreenshotEnabled(context))
        CropFeaturePreferenceManager.setLongScreenshotEnabled(context, false)
        assertFalse(CropFeaturePreferenceManager.isLongScreenshotEnabled(context))
    }

    @Test
    fun testLongScreenshotStitcherStitch() {
        val bmp1 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val bmp2 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }

        val stitched = LongScreenshotStitcher.stitch(listOf(bmp1, bmp2), statusBarHeightPx = 20, navBarHeightPx = 20)
        assertNotNull(stitched)
        assertEquals(100, stitched.width)
        // bmp1 slice: 200 - 20 = 180. bmp2 slice: 200 - 20 = 180. Total = 360
        assertEquals(360, stitched.height)
    }

    @Test
    fun testLongScreenshotStitcherAppendSegment() {
        val base = Bitmap.createBitmap(100, 300, Bitmap.Config.ARGB_8888)
        val next = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)

        val extended = LongScreenshotStitcher.appendSegment(base, next, statusBarHeightPx = 25, navBarHeightPx = 15)
        assertNotNull(extended)
        assertEquals(100, extended.width)
        assertEquals(300 + (200 - 25), extended.height)
    }

    @Test
    fun testScreenshotHolderProperties() {
        val bmp = Bitmap.createBitmap(100, 500, Bitmap.Config.ARGB_8888)
        ScreenshotHolder.bitmap = bmp
        ScreenshotHolder.isLongScreenshot = true
        ScreenshotHolder.pageCount = 3

        assertEquals(bmp, ScreenshotHolder.bitmap)
        assertTrue(ScreenshotHolder.isLongScreenshot)
        assertEquals(3, ScreenshotHolder.pageCount)

        ScreenshotHolder.clear()
        assertEquals(null, ScreenshotHolder.bitmap)
        assertFalse(ScreenshotHolder.isLongScreenshot)
        assertEquals(1, ScreenshotHolder.pageCount)
    }
}
