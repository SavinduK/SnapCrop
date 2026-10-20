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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedTemporaryStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BatchCropManager.clearBatch(context)
    }

    @Test
    fun testRetentionPolicyAndTemporaryItems() {
        assertEquals(24 * 60 * 60 * 1000L, BatchCropManager.RETENTION_PERIOD_MS)

        val bmp1 = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val file1 = BatchCropManager.addCroppedBitmapToBatch(context, bmp1)
        assertNotNull(file1)
        assertTrue(file1!!.exists())

        val items = BatchCropManager.getTemporaryItems(context)
        assertEquals(1, items.size)
        assertEquals(file1.absolutePath, items[0].file.absolutePath)
        assertTrue(items[0].remainingTimeMs > 0)

        // Delete single file
        val deleted = BatchCropManager.deleteBatchFile(context, file1)
        assertTrue(deleted)
        assertEquals(0, BatchCropManager.getBatchCount(context))
    }

    @Test
    fun testMultiSelectBatchActions() {
        val bmp1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val file1 = BatchCropManager.addCroppedBitmapToBatch(context, bmp1)
        val file2 = BatchCropManager.addCroppedBitmapToBatch(context, bmp2)
        assertNotNull(file1)
        assertNotNull(file2)

        val files = listOf(file1!!, file2!!)
        val shareIntent = BatchCropManager.createShareIntentForFiles(context, files)
        assertNotNull(shareIntent)

        val deletedCount = BatchCropManager.deleteBatchFiles(context, files)
        assertEquals(2, deletedCount)
        assertEquals(0, BatchCropManager.getBatchCount(context))
    }

    @Test
    fun testExpiredFilesCleanup() {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val file = BatchCropManager.addCroppedBitmapToBatch(context, bmp)
        assertNotNull(file)

        // Set file modification time to 25 hours ago
        val pastTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        file!!.setLastModified(pastTimestamp)

        // Calling getBatchFiles should purge the expired file automatically
        val remainingFiles = BatchCropManager.getBatchFiles(context)
        assertEquals(0, remainingFiles.size)
        assertEquals(0, BatchCropManager.getBatchCount(context))
    }
}
