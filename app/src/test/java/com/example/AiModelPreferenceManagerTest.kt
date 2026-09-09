package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiModelPreferenceManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("snapcrop_ai_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `default AI model is GEMINI`() {
        val model = AiModelPreferenceManager.getSelectedModel(context)
        assertEquals(AiModelPreferenceManager.AiModel.GEMINI, model)
    }

    @Test
    fun `setting CHATGPT updates preferences correctly`() {
        AiModelPreferenceManager.setSelectedModel(
            context,
            AiModelPreferenceManager.AiModel.CHATGPT
        )
        val model = AiModelPreferenceManager.getSelectedModel(context)
        assertEquals(AiModelPreferenceManager.AiModel.CHATGPT, model)
    }

    @Test
    fun `setting CLAUDE updates preferences correctly`() {
        AiModelPreferenceManager.setSelectedModel(
            context,
            AiModelPreferenceManager.AiModel.CLAUDE
        )
        val model = AiModelPreferenceManager.getSelectedModel(context)
        assertEquals(AiModelPreferenceManager.AiModel.CLAUDE, model)
    }
}
