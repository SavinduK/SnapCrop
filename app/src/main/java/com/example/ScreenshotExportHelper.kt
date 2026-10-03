package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ScreenshotExportHelper {

    private const val TAG = "ScreenshotExportHelper"

    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val filename = "SnapCrop_Long_$timestamp.png"

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
                Toast.makeText(context, context.getString(R.string.toast_saved_to_gallery), Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot to gallery", e)
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun share(context: Context, bitmap: Bitmap) {
        try {
            val imagesDir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
            val file = File(imagesDir, "share_long_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.applicationContext.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setDataAndType(contentUri, "image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(context.contentResolver, "Long Screenshot", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Long Screenshot").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share long screenshot", e)
            Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val imagesDir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
            val file = File(imagesDir, "clipboard_long.png")
            FileOutputStream(file).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.applicationContext.packageName}.fileprovider",
                file
            )

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, "Long Screenshot", contentUri)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy long screenshot", e)
            Toast.makeText(context, "Failed to copy image", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun delete(context: Context) {
        try {
            ScreenshotHolder.clear()
            val imagesDir = File(context.cacheDir, "images")
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { file ->
                    if (file.name.contains("long")) {
                        file.delete()
                    }
                }
            }
            Toast.makeText(context, "Long screenshot deleted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete long screenshot", e)
        }
    }
}
