package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatchCropTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BatchCropManager.clearBatch(context)
    }

    @Test
    fun testBatchCropManagerAddCountAndClear() {
        assertEquals(0, BatchCropManager.getBatchCount(context))

        val bmp1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val file1 = BatchCropManager.addCroppedBitmapToBatch(context, bmp1)
        assertNotNull(file1)
        assertTrue(file1!!.exists())
        assertEquals(1, BatchCropManager.getBatchCount(context))

        val bmp2 = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val file2 = BatchCropManager.addCroppedBitmapToBatch(context, bmp2)
        assertNotNull(file2)
        assertTrue(file2!!.exists())
        assertEquals(2, BatchCropManager.getBatchCount(context))

        val batchFiles = BatchCropManager.getBatchFiles(context)
        assertEquals(2, batchFiles.size)

        // Clear batch
        BatchCropManager.clearBatch(context)
        assertEquals(0, BatchCropManager.getBatchCount(context))
        assertEquals(0, BatchCropManager.getBatchFiles(context).size)
    }

    @Test
    fun testCreateShareBatchIntent() {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        BatchCropManager.addCroppedBitmapToBatch(context, bmp)
        assertEquals(1, BatchCropManager.getBatchCount(context))

        val extraBmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val shareIntent = BatchCropManager.createShareBatchChooserIntent(context, extraBmp)
        assertNotNull(shareIntent)
    }

    @Test
    fun testBatchStringsExist() {
        val countText = context.getString(R.string.crop_batch_banner_title, 3)
        assertEquals("Batch: 3 saved", countText)

        val countWithNewText = context.getString(R.string.crop_batch_banner_title_with_new, 3)
        assertEquals("Batch: 3 saved + 1 new", countWithNewText)

        val toggleText = context.getString(R.string.crop_btn_toggle_batch)
        assertEquals("Batch Actions", toggleText)
    }
}
