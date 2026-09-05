package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TriggerPreferenceManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset to default
        context.getSharedPreferences("snapcrop_trigger_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `default trigger mode is EDGE_PANEL with edge panel enabled`() {
        val mode = TriggerPreferenceManager.getTriggerMode(context)
        assertEquals(TriggerPreferenceManager.TriggerMode.EDGE_PANEL, mode)
        assertTrue(TriggerPreferenceManager.isEdgePanelEnabled(context))
        assertFalse(TriggerPreferenceManager.isPowerTripleTapEnabled(context))
    }

    @Test
    fun `setting POWER_BUTTON_TRIPLE_TAP updates preferences correctly`() {
        TriggerPreferenceManager.setTriggerMode(
            context,
            TriggerPreferenceManager.TriggerMode.POWER_BUTTON_TRIPLE_TAP
        )

        val mode = TriggerPreferenceManager.getTriggerMode(context)
        assertEquals(TriggerPreferenceManager.TriggerMode.POWER_BUTTON_TRIPLE_TAP, mode)
        assertTrue(TriggerPreferenceManager.isPowerTripleTapEnabled(context))
        assertFalse(TriggerPreferenceManager.isEdgePanelEnabled(context))
    }
}

