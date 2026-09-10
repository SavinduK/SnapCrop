package com.example

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Manages storing, retrieving, clearing, batch saving to Gallery,
 * and batch sharing of multiple cropped screenshots in temporary app storage.
 */
object BatchCropManager {
    private const val TAG = "BatchCropManager"
    private const val BATCH_DIR_NAME = "batch_crops"

    fun getBatchDir(context: Context): File {
        val dir = File(context.cacheDir, BATCH_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Returns list of saved batch files ordered by timestamp (oldest first).
     */
    fun getBatchFiles(context: Context): List<File> {
        val dir = getBatchDir(context)
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".png") }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Returns the count of currently saved batch images.
     */
    fun getBatchCount(context: Context): Int {
        return getBatchFiles(context).size
    }

    /**
     * Stores a cropped bitmap into app's temporary internal storage.
     * Returns the generated File if successful, null otherwise.
     */
    fun addCroppedBitmapToBatch(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = getBatchDir(context)
            val file = File(dir, "batch_crop_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            Log.d(TAG, "Added cropped bitmap to batch storage: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cropped bitmap to batch storage", e)
            null
        }
    }

    /**
     * Clears all temporary saved batch images.
     * Returns the count of deleted files.
     */
    fun clearBatch(context: Context): Int {
        val files = getBatchFiles(context)
        var deleted = 0
        for (f in files) {
            if (f.delete()) {
                deleted++
            }
        }
        Log.d(TAG, "Cleared batch storage: $deleted files deleted")
        return deleted
    }

    /**
     * Saves all images in the batch (plus an optional additional bitmap)
     * to the system Gallery (Pictures/SnapCrop).
     * Returns total count of images saved.
     */
    fun saveBatchToGallery(context: Context, additionalBitmap: Bitmap? = null): Int {
        val files = getBatchFiles(context)
        var savedCount = 0

        for (file in files) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
            if (saveBitmapToMediaStore(context, bitmap)) {
                savedCount++
            }
            bitmap.recycle()
        }

        if (additionalBitmap != null) {
            if (saveBitmapToMediaStore(context, additionalBitmap)) {
                savedCount++
            }
        }

        return savedCount
    }

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val filename = "SnapCrop_batch_${timestamp}_${(100..999).random()}.png"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SnapCrop")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = context.contentResolver.insert(collectionUri, values)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to MediaStore", e)
            false
        }
    }

    /**
     * Prepares an ACTION_SEND_MULTIPLE chooser intent to share all batch files
     * (and optionally saves additionalBitmap into batch first).
     */
    fun createShareBatchChooserIntent(context: Context, additionalBitmap: Bitmap? = null): Intent? {
        if (additionalBitmap != null) {
            addCroppedBitmapToBatch(context, additionalBitmap)
        }

        val files = getBatchFiles(context)
        if (files.isEmpty()) return null

        val uris = ArrayList<Uri>()
        for (f in files) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.applicationContext.packageName}.fileprovider",
                    f
                )
                uris.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get URI for batch file: ${f.name}", e)
            }
        }

        if (uris.isEmpty()) return null

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            val clipData = ClipData.newUri(context.contentResolver, "Batch Screenshots", uris[0])
            for (i in 1 until uris.size) {
                clipData.addItem(ClipData.Item(uris[i]))
            }
            setClipData(clipData)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = "Share ${uris.size} Screenshots"
        return Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TITLE, chooserTitle)
        }
    }
}
