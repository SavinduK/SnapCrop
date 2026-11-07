package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SelectionToolbarLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreenshotHolder.clear()
        BatchCropManager.clearBatch(context)
    }

    @Test
    fun `search icon is moved into the select bar as the left icon, and removed from the top bar`() {
        val sampleBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val testBox = RectF(100f, 300f, 300f, 500f)
        var searchInvoked = false

        composeTestRule.setContent {
            MaterialTheme {
                CropOverlayContent(
                    screenshot = sampleBitmap,
                    initialSelectionRect = testBox,
                    onExtractBitmap = { sampleBitmap },
                    onSearchGoogleImage = { searchInvoked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Search icon is NOT in the top bar
        composeTestRule.onNodeWithTag("btn_search_image_top").assertDoesNotExist()

        // 2. Search icon is present inside the floating action toolbar (the select bar)
        val searchNode = composeTestRule.onNodeWithTag("btn_search_crop")
        val editNode = composeTestRule.onNodeWithTag("btn_edit_crop")
        searchNode.assertIsDisplayed()
        editNode.assertIsDisplayed()

        // 3. Search icon is the LEFT icon (its right edge is <= edit button's left edge)
        val searchBounds = searchNode.fetchSemanticsNode().boundsInRoot
        val editBounds = editNode.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected search icon to be located to the left of the edit icon in select bar",
            searchBounds.left < editBounds.left
        )

        // 4. Clicking search icon triggers image search
        searchNode.performClick()
        composeTestRule.waitForIdle()
        assertTrue("Expected onSearchGoogleImage to be invoked", searchInvoked)
    }

    @Test
    fun `text icon is positioned above the selected box top right with a small label`() {
        val sampleBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        // Selection box located within screen bounds: left = 60f, top = 150f, right = 260f, bottom = 350f
        val testBox = RectF(60f, 150f, 260f, 350f)
        var ocrRequested = false

        composeTestRule.setContent {
            MaterialTheme {
                CropOverlayContent(
                    screenshot = sampleBitmap,
                    initialSelectionRect = testBox,
                    onExtractBitmap = { sampleBitmap },
                    onOcrRequested = { rect, _, _, _ ->
                        ocrRequested = true
                        assertEquals(testBox, rect)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Text icon pill is displayed with the small "Text" label
        val textPillNode = composeTestRule.onNodeWithTag("btn_select_text_crop")
        textPillNode.assertIsDisplayed()
        textPillNode.assertTextContains("Text")

        // 2. Positioned above the selected box and aligned to the top right
        val textBounds = textPillNode.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected text icon pill top to be above the selected box (top < testBox.top)",
            textBounds.top < testBox.top
        )
        assertTrue(
            "Expected text icon pill to be positioned above the selected box (bottom <= testBox.top), got textBounds.bottom=${textBounds.bottom} and testBox.top=${testBox.top}",
            textBounds.bottom <= testBox.top
        )
        assertTrue(
            "Expected text icon pill to be aligned near the right side of the box (right >= testBox.left)",
            textBounds.right >= testBox.left
        )

        // 3. Clicking the text icon triggers onOcrRequested
        textPillNode.performClick()
        composeTestRule.waitForIdle()
        assertTrue("Expected onOcrRequested to be invoked on clicking text pill", ocrRequested)
    }

    @Test
    fun `text recognition option can be toggled off and on via preference and CropOverlayContent respects it`() {
        val sampleBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val testBox = RectF(60f, 150f, 260f, 350f)

        // 1. By default, preference is enabled
        CropFeaturePreferenceManager.setTextRecognitionEnabled(context, true)
        assertTrue(CropFeaturePreferenceManager.isTextRecognitionEnabled(context))

        // 2. Set OCR disabled in preference and verify pill is hidden
        CropFeaturePreferenceManager.setTextRecognitionEnabled(context, false)
        org.junit.Assert.assertFalse(CropFeaturePreferenceManager.isTextRecognitionEnabled(context))

        composeTestRule.setContent {
            MaterialTheme {
                CropOverlayContent(
                    screenshot = sampleBitmap,
                    initialSelectionRect = testBox,
                    onExtractBitmap = { sampleBitmap }
                )
            }
        }
        composeTestRule.waitForIdle()

        // btn_select_text_crop should not exist when OCR is disabled
        composeTestRule.onNodeWithTag("btn_select_text_crop").assertDoesNotExist()

        // floating_action_toolbar (select bar) should still be displayed
        composeTestRule.onNodeWithTag("floating_action_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_search_crop").assertIsDisplayed()
    }

    @Test
    fun `main activity displays text recognition toggle option and clicking updates preference`() {
        CropFeaturePreferenceManager.setTextRecognitionEnabled(context, true)

        composeTestRule.setContent {
            MaterialTheme {
                SnapCropMainScreen()
            }
        }
        composeTestRule.waitForIdle()

        val toggleNode = composeTestRule.onNodeWithTag("toggle_text_recognition")
        toggleNode.performScrollTo()
        toggleNode.assertIsDisplayed()
        toggleNode.assertTextContains("Text Recognition (OCR)")

        // Toggle it OFF
        toggleNode.performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertFalse(CropFeaturePreferenceManager.isTextRecognitionEnabled(context))

        // Toggle it back ON
        toggleNode.performClick()
        composeTestRule.waitForIdle()
        assertTrue(CropFeaturePreferenceManager.isTextRecognitionEnabled(context))
    }
}
