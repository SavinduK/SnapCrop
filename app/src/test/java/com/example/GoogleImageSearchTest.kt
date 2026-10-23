package com.example

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleImageSearchTest {

    @Test
    fun `when Google Search is installed, sends photo directly to Lens activity without share chooser`() {
        val controller = Robolectric.buildActivity(CropOverlayActivity::class.java).setup()
        val activity = controller.get()
        val shadowPm = shadowOf(activity.packageManager)

        val googlePkg = "com.google.android.googlequicksearchbox"
        val packageInfo = PackageInfo().apply {
            packageName = googlePkg
            applicationInfo = ApplicationInfo().apply {
                packageName = googlePkg
                enabled = true
            }
        }
        shadowPm.installPackage(packageInfo)

        // Register both a Gemini activity and a Lens activity inside Google app
        val geminiComp = ComponentName(googlePkg, "com.google.android.apps.bard.ShareActivity")
        val geminiInfo = ActivityInfo().apply {
            packageName = googlePkg
            name = geminiComp.className
            applicationInfo = packageInfo.applicationInfo
            exported = true
        }
        shadowPm.addOrUpdateActivity(geminiInfo)
        shadowPm.addIntentFilterForActivity(
            geminiComp,
            IntentFilter(Intent.ACTION_SEND).apply { addDataType("image/*") }
        )

        val lensComp = ComponentName(googlePkg, "com.google.android.apps.search.lens.LensShareEntryPointActivity")
        val lensInfo = ActivityInfo().apply {
            packageName = googlePkg
            name = lensComp.className
            applicationInfo = packageInfo.applicationInfo
            exported = true
        }
        shadowPm.addOrUpdateActivity(lensInfo)
        shadowPm.addIntentFilterForActivity(
            lensComp,
            IntentFilter(Intent.ACTION_SEND).apply { addDataType("image/*") }
        )

        val testBitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        activity.handleSearchGoogleWithImage(testBitmap)

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull("Expected an intent to be started", startedIntent)
        assertNotEquals("Must never open system share chooser", Intent.ACTION_CHOOSER, startedIntent.action)
        assertEquals(Intent.ACTION_SEND, startedIntent.action)
        assertEquals(lensComp, startedIntent.component)
        assertNotNull(androidx.core.content.IntentCompat.getParcelableExtra(startedIntent, Intent.EXTRA_STREAM, Uri::class.java))
    }

    @Test
    fun `when Google Search is not installed, directs user to Play Store instead of share menu`() {
        val controller = Robolectric.buildActivity(CropOverlayActivity::class.java).setup()
        val activity = controller.get()

        val testBitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        activity.handleSearchGoogleWithImage(testBitmap)

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull("Expected Play Store intent to be started", startedIntent)
        assertNotEquals("Must never open system share chooser", Intent.ACTION_CHOOSER, startedIntent.action)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        val dataStr = startedIntent.dataString ?: ""
        assertTrue(
            "Expected Play Store URL/URI for Google Search, got: $dataStr",
            dataStr.contains("com.google.android.googlequicksearchbox")
        )
    }
}
