package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraAndCropFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BatchCropManager.clearBatch(context)
        ScreenshotHolder.clear()
        CameraCaptureActivity.testInitialCapturedBitmap = null
    }

    @Test
    fun `main screen removes Share to AI, AI model cards, and Math Solver completely`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SnapCropMainScreen()
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setup_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_share_to_ai").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_math_solver").assertDoesNotExist()
        composeTestRule.onNodeWithTag("ai_card_gemini").assertDoesNotExist()
        composeTestRule.onNodeWithTag("ai_card_chatgpt").assertDoesNotExist()
        composeTestRule.onNodeWithTag("ai_card_claude").assertDoesNotExist()
    }

    @Test
    fun `NormalizedCropRect extracts full and sub-cropped bitmaps accurately`() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)

        val fullExtract = NormalizedCropRect.FULL.extractFromBitmap(source)
        assertEquals(200, fullExtract.width)
        assertEquals(100, fullExtract.height)

        val subRect = NormalizedCropRect(left = 0.25f, top = 0.20f, right = 0.75f, bottom = 0.80f)
        val cropped = subRect.extractFromBitmap(source)
        assertEquals(100, cropped.width)
        assertEquals(60, cropped.height)
    }

    @Test
    fun `crop overlay places camera icon right of saved clips icon and launches overlay camera mode`() {
        ScreenshotHolder.bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)

        ActivityScenario.launch(CropOverlayActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()

            val galleryNode = composeTestRule.onNodeWithTag("btn_gallery_top")
            val cameraNode = composeTestRule.onNodeWithTag("btn_camera_top")

            galleryNode.assertIsDisplayed()
            cameraNode.assertIsDisplayed()

            val galleryBounds = galleryNode.fetchSemanticsNode().boundsInRoot
            val cameraBounds = cameraNode.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Expected camera icon to be positioned right of Saved Clips icon",
                cameraBounds.left >= galleryBounds.right
            )

            cameraNode.performClick()
            composeTestRule.waitForIdle()

            scenario.onActivity { activity ->
                val startedIntent = shadowOf(activity).nextStartedActivity
                assertNotNull("Expected CameraCaptureActivity intent to be started", startedIntent)
                assertEquals(
                    CameraCaptureActivity::class.java.name,
                    startedIntent.component?.className
                )
                assertEquals(
                    CameraCaptureActivity.MODE_OVERLAY,
                    startedIntent.getStringExtra(CameraCaptureActivity.EXTRA_LAUNCH_MODE)
                )
            }
        }
    }

    @Test
    fun `saved clips screen displays camera icon in top right title bar`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SavedImagesScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        val refreshNode = composeTestRule.onNodeWithTag("btn_refresh_saved_images")
        val cameraNode = composeTestRule.onNodeWithTag("btn_camera_saved_images")

        refreshNode.assertIsDisplayed()
        cameraNode.assertIsDisplayed()

        val refreshBounds = refreshNode.fetchSemanticsNode().boundsInRoot
        val cameraBounds = cameraNode.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected camera icon on the top-right end of the Saved Clips header",
            cameraBounds.left >= refreshBounds.right
        )
    }

    @Test
    fun `camera capture in Saved Clips mode saves cropped photo into Saved Clips`() {
        val capturedBmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        var completed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CameraCaptureScreen(
                    launchMode = CameraCaptureActivity.MODE_SAVED_CLIPS,
                    initialCapturedBitmap = capturedBmp,
                    onClose = {},
                    onSavedClipComplete = { completed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_camera_save_to_clips").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue("Expected onSavedClipComplete callback", completed)
        assertEquals(1, BatchCropManager.getBatchCount(context))
    }

    @Test
    fun `camera capture in Overlay mode provides Saved Clips, Gallery, Copy, and Google Search when installed`() {
        val googlePkg = "com.google.android.googlequicksearchbox"
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = googlePkg
                applicationInfo = ApplicationInfo().apply {
                    packageName = googlePkg
                    enabled = true
                }
            }
        )

        val capturedBmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)

        composeTestRule.setContent {
            MyApplicationTheme {
                CameraCaptureScreen(
                    launchMode = CameraCaptureActivity.MODE_OVERLAY,
                    initialCapturedBitmap = capturedBmp,
                    onClose = {},
                    onSavedClipComplete = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_camera_save_clips").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_save_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_copy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_google_search").assertIsDisplayed()

        // Clicking Save to Saved Clips stores the image in BatchCropManager
        composeTestRule.onNodeWithTag("btn_camera_save_clips").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, BatchCropManager.getBatchCount(context))
    }
}
