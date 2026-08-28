package com.example

import android.graphics.Bitmap

/**
 * ScreenshotHolder
 *
 * In-memory holder for captured screenshot bitmaps.
 * Allows CropOverlayActivity to access and render the full-resolution screenshot
 * in 0ms on the very first frame without waiting for disk I/O, eliminating any
 * flash of background or app screens.
 */
object ScreenshotHolder {
    @Volatile
    var bitmap: Bitmap? = null

    fun clear() {
        bitmap = null
    }
}
