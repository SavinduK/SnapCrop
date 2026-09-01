package com.example

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
class OcrFeatureTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `copy text to clipboard stores correct text`() {
        val testText = "Hello SnapCrop OCR text recognition"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SnapCrop OCR", testText)
        clipboard.setPrimaryClip(clip)

        val primaryClip = clipboard.primaryClip
        assertNotNull(primaryClip)
        assertEquals(1, primaryClip!!.itemCount)
        assertEquals(testText, primaryClip.getItemAt(0).text.toString())
    }

    @Test
    fun `google search intent has correct action and query extra`() {
        val query = "Quantum Computing in 2026"
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        assertEquals(Intent.ACTION_WEB_SEARCH, searchIntent.action)
        assertEquals(query, searchIntent.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun `share text intent creates valid text plain action send`() {
        val textToShare = "Important notes extracted from screen"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, textToShare)
            type = "text/plain"
        }

        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("text/plain", sendIntent.type)
        assertEquals(textToShare, sendIntent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
