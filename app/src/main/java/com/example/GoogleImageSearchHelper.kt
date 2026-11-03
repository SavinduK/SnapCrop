package com.example

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Helper for sending captured or cropped images directly to Google Search (Google Lens visual search)
 * without opening the generic Android system share chooser.
 */
object GoogleImageSearchHelper {
    private const val TAG = "GoogleImageSearchHelper"
    const val GOOGLE_SEARCH_PKG = "com.google.android.googlequicksearchbox"
    const val GOOGLE_LENS_PKG = "com.google.ar.lens"
    private const val PLAY_STORE_PKG = "com.android.vending"

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            try {
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isGoogleSearchInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val googleResolved = pm.queryIntentActivities(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                setPackage(GOOGLE_SEARCH_PKG)
            },
            0
        )
        val lensResolved = pm.queryIntentActivities(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                setPackage(GOOGLE_LENS_PKG)
            },
            0
        )
        return googleResolved.isNotEmpty() ||
            lensResolved.isNotEmpty() ||
            isPackageInstalled(context, GOOGLE_SEARCH_PKG) ||
            isPackageInstalled(context, GOOGLE_LENS_PKG)
    }

    /**
     * Sends [bitmap] directly to Google Search / Google Lens.
     * Never opens a generic share chooser. If Google is not installed and [openPlayStoreIfMissing]
     * is true, directs the user to Google Play Store.
     */
    fun searchWithImage(
        context: Context,
        bitmap: Bitmap,
        openPlayStoreIfMissing: Boolean = true
    ): Boolean {
        val pm = context.packageManager
        try {
            val googleResolved = pm.queryIntentActivities(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    setPackage(GOOGLE_SEARCH_PKG)
                },
                0
            )
            val lensResolved = pm.queryIntentActivities(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    setPackage(GOOGLE_LENS_PKG)
                },
                0
            )
            val isGoogleInstalled = googleResolved.isNotEmpty() ||
                isPackageInstalled(context, GOOGLE_SEARCH_PKG)
            val isLensInstalled = lensResolved.isNotEmpty() ||
                isPackageInstalled(context, GOOGLE_LENS_PKG)

            if (!isGoogleInstalled && !isLensInstalled) {
                if (openPlayStoreIfMissing) {
                    Toast.makeText(
                        context,
                        "Google Search not installed. Opening Play Store…",
                        Toast.LENGTH_SHORT
                    ).show()
                    openGoogleSearchInPlayStore(context)
                } else {
                    Toast.makeText(
                        context,
                        "Google Search is not installed on this device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return false
            }

            val imagesDir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
            val imageFile = File(imagesDir, "google_search_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }

            val contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.applicationContext.packageName}.fileprovider",
                    imageFile
                )
            } catch (_: Exception) {
                Uri.fromFile(imageFile)
            }

            listOf(GOOGLE_SEARCH_PKG, GOOGLE_LENS_PKG).forEach { pkg ->
                try {
                    context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not pre-grant URI permission to $pkg", e)
                }
            }

            fun createImageSendIntent(): Intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newUri(context.contentResolver, "Google Search", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            var launched = false

            // Strategy 1: Launch the exact Google Lens / "Search image" component directly so no chooser appears.
            for (resolvedList in listOf(googleResolved, lensResolved)) {
                if (launched || resolvedList.isEmpty()) continue
                val sortedCandidates = resolvedList.sortedByDescending { info ->
                    val className = info.activityInfo?.name?.lowercase() ?: ""
                    val label = try {
                        info.loadLabel(pm).toString().lowercase()
                    } catch (_: Exception) {
                        ""
                    }
                    when {
                        className.contains("lens") -> 100
                        label.contains("lens") || label.contains("search image") -> 90
                        className.contains("gemini") || className.contains("bard") || className.contains("robin") -> -50
                        label.contains("gemini") -> -50
                        className.contains("search") || label.contains("search") -> 50
                        else -> 10
                    }
                }

                for (candidate in sortedCandidates) {
                    val actInfo = candidate.activityInfo ?: continue
                    val explicitIntent = createImageSendIntent().apply {
                        component = ComponentName(actInfo.packageName, actInfo.name)
                    }
                    try {
                        context.startActivity(explicitIntent)
                        launched = true
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "Explicit component launch failed for ${actInfo.name}", e)
                    }
                }
            }

            // Strategy 2: Known Google Lens entry points and direct package intents (never chooser).
            if (!launched) {
                if (isGoogleInstalled) {
                    val knownLensComponents = listOf(
                        ComponentName(
                            GOOGLE_SEARCH_PKG,
                            "com.google.android.apps.search.lens.LensShareEntryPointActivity"
                        ),
                        ComponentName(
                            GOOGLE_SEARCH_PKG,
                            "com.google.android.apps.search.lens.LensExportedActivity"
                        )
                    )
                    for (comp in knownLensComponents) {
                        if (launched) break
                        try {
                            context.startActivity(createImageSendIntent().apply { component = comp })
                            launched = true
                        } catch (e: Exception) {
                            Log.d(TAG, "Known Lens component $comp failed", e)
                        }
                    }
                }

                if (!launched && isLensInstalled) {
                    try {
                        context.startActivity(createImageSendIntent().apply { setPackage(GOOGLE_LENS_PKG) })
                        launched = true
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct package send to Google Lens failed", e)
                    }
                }

                if (!launched && isGoogleInstalled) {
                    try {
                        context.startActivity(createImageSendIntent().apply { setPackage(GOOGLE_SEARCH_PKG) })
                        launched = true
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct package send to Google Search failed", e)
                    }
                }
            }

            if (!launched && openPlayStoreIfMissing) {
                Toast.makeText(
                    context,
                    "Google Search not installed. Opening Play Store…",
                    Toast.LENGTH_SHORT
                ).show()
                openGoogleSearchInPlayStore(context)
            }
            return launched
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search with image", e)
            Toast.makeText(context, "Error opening Google Search", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun openGoogleSearchInPlayStore(context: Context) {
        val marketUri = Uri.parse("market://details?id=$GOOGLE_SEARCH_PKG")

        if (isPackageInstalled(context, PLAY_STORE_PKG)) {
            try {
                val playStoreAppIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                    setPackage(PLAY_STORE_PKG)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playStoreAppIntent)
                return
            } catch (_: Exception) {
            }
        }

        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            return
        } catch (_: Exception) {
        }

        try {
            val webPlayStoreUri = Uri.parse(
                "https://play.google.com/store/apps/details?id=$GOOGLE_SEARCH_PKG"
            )
            val webIntent = Intent(Intent.ACTION_VIEW, webPlayStoreUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open Play Store for Google Search", e)
        }
    }
}
