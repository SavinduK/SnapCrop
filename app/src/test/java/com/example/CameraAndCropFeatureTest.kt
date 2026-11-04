package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
        val gapPx = cameraBounds.left - refreshBounds.right
        assertTrue(
            "Expected generous spacing (>= 16px) between refresh and camera icons in Saved Clips title bar, got $gapPx",
            gapPx >= 16f
        )
    }

    @Test
    fun `saved clips card hides Open text and clicking card opens Saved Clips screen`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SnapCropMainScreen()
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Open").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_nav_saved_images").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("saved_images_screen").assertIsDisplayed()
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
        var closeCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                CameraCaptureScreen(
                    launchMode = CameraCaptureActivity.MODE_OVERLAY,
                    initialCapturedBitmap = capturedBmp,
                    onClose = { closeCount++ },
                    onSavedClipComplete = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_camera_save_clips").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_save_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_copy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_google_search").assertIsDisplayed()

        // Clicking every action button in Overlay camera menu closes the screen
        composeTestRule.onNodeWithTag("btn_camera_save_clips").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, BatchCropManager.getBatchCount(context))
        assertEquals(1, closeCount)

        composeTestRule.onNodeWithTag("btn_camera_copy").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, closeCount)

        composeTestRule.onNodeWithTag("btn_camera_save_gallery").performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, closeCount)

        composeTestRule.onNodeWithTag("btn_camera_google_search").performClick()
        composeTestRule.waitForIdle()
        assertEquals(4, closeCount)
    }

    @Test
    fun `clicking Copy in Overlay CameraCaptureActivity finishes the activity`() {
        CameraCaptureActivity.testInitialCapturedBitmap =
            Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val intent = CameraCaptureActivity.createIntent(context, CameraCaptureActivity.MODE_OVERLAY)

        ActivityScenario.launch<CameraCaptureActivity>(intent).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("btn_camera_copy").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()

            scenario.onActivity { activity ->
                assertTrue(
                    "Expected CameraCaptureActivity to finish after clicking Copy",
                    activity.isFinishing
                )
            }
        }
    }

    @Test
    fun `clicking Save in Saved Clips CameraCaptureActivity finishes the activity`() {
        CameraCaptureActivity.testInitialCapturedBitmap =
            Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val intent = CameraCaptureActivity.createIntent(context, CameraCaptureActivity.MODE_SAVED_CLIPS)

        ActivityScenario.launch<CameraCaptureActivity>(intent).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("btn_camera_save_to_clips").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()

            scenario.onActivity { activity ->
                assertTrue(
                    "Expected CameraCaptureActivity to finish after saving to Saved Clips",
                    activity.isFinishing
                )
            }
            assertEquals(1, BatchCropManager.getBatchCount(context))
        }
    }

    @Test
    fun `samsung camera zoom bar supports preset buttons and tick-mark scrubber slider`() {
        var currentZoom by mutableFloatStateOf(1.0f)
        var expanded by mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                SamsungCameraZoomBar(
                    zoomRatio = currentZoom,
                    minZoom = 1.0f,
                    maxZoom = 8.0f,
                    isExpanded = expanded,
                    onExpandedChange = { expanded = it },
                    onDraggingChange = {},
                    onZoomChange = { currentZoom = it },
                    onInteractionTick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("camera_zoom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_zoom_1x").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_zoom_2x").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_zoom_4x").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_camera_zoom_8x").assertIsDisplayed()

        // Tap 2x preset -> updates zoom to 2.0f and expands the Samsung tick-mark ruler
        composeTestRule.onNodeWithTag("btn_camera_zoom_2x").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2.0f, currentZoom, 0.05f)
        assertTrue("Expected Samsung tick-mark ruler to expand on preset selection", expanded)
        composeTestRule.onNodeWithTag("camera_zoom_slider").assertIsDisplayed()

        // Scrub right across the expanded tick-mark ruler -> increases zoom past 2.0x
        composeTestRule.onNodeWithTag("camera_zoom_slider").performTouchInput {
            swipeRight(startX = width * 0.35f, endX = width * 0.85f)
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "Expected scrubbing right on zoom slider to increase zoom above 2.0x, got $currentZoom",
            currentZoom > 2.5f
        )

        // Tap near the far right of the ruler -> jumps close to max zoom
        composeTestRule.onNodeWithTag("camera_zoom_slider").performTouchInput {
            down(Offset(width * 0.96f, centerY))
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected tapping near right end of zoom ruler to approach 8x, got $currentZoom", currentZoom >= 6.5f)
    }
}
