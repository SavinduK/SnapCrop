package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * TriggerPreferenceManager
 *
 * Persists the user's screenshot trigger configuration.
 * Default is SLIDER (Horizontal Edge Slider docked at screen edge).
 */
object TriggerPreferenceManager {
    private const val PREFS_NAME = "snapcrop_trigger_prefs"
    private const val KEY_TRIGGER_MODE = "trigger_mode"

    enum class TriggerMode {
        SLIDER   // Default: Horizontal edge slider bar docked on screen
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTriggerMode(context: Context): TriggerMode {
        val modeStr = getPrefs(context).getString(KEY_TRIGGER_MODE, TriggerMode.SLIDER.name)
        return try {
            TriggerMode.valueOf(modeStr ?: TriggerMode.SLIDER.name)
        } catch (e: Exception) {
            TriggerMode.SLIDER
        }
    }

    fun setTriggerMode(context: Context, mode: TriggerMode) {
        getPrefs(context).edit().putString(KEY_TRIGGER_MODE, mode.name).apply()
    }

    fun isVolumeDownEnabled(context: Context): Boolean {
        return false // Volume down long-press removed per user instruction
    }

    fun isThreeFingerEnabled(context: Context): Boolean {
        return true // Horizontal edge slider is default and active
    }
}

