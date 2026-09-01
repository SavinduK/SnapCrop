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
    fun `default trigger mode is SLIDER with three finger slider enabled`() {
        val mode = TriggerPreferenceManager.getTriggerMode(context)
        assertEquals(TriggerPreferenceManager.TriggerMode.SLIDER, mode)
        assertFalse(TriggerPreferenceManager.isVolumeDownEnabled(context))
        assertTrue(TriggerPreferenceManager.isThreeFingerEnabled(context))
    }

    @Test
    fun `setting SLIDER updates preferences correctly`() {
        TriggerPreferenceManager.setTriggerMode(context, TriggerPreferenceManager.TriggerMode.SLIDER)

        val mode = TriggerPreferenceManager.getTriggerMode(context)
        assertEquals(TriggerPreferenceManager.TriggerMode.SLIDER, mode)
        assertTrue(TriggerPreferenceManager.isThreeFingerEnabled(context))
    }
}

