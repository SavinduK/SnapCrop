package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * TriggerPreferenceManager
 *
 * Persists the user's preferred screenshot trigger mode.
 * Default is VOLUME_DOWN_ONLY, guaranteeing 100% untouched OS screen interaction
 * by keeping the touch overlay completely unattached or with FLAG_NOT_TOUCHABLE.
 */
object TriggerPreferenceManager {
    private const val PREFS_NAME = "snapcrop_trigger_prefs"
    private const val KEY_TRIGGER_MODE = "trigger_mode"

    enum class TriggerMode {
        VOLUME_DOWN_ONLY,   // Default: Volume Down long-press only (100% untouched OS screen touches)
        THREE_FINGER_SWIPE,  // 3-Finger downward swipe overlay
        BOTH                // Both triggers enabled simultaneously
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTriggerMode(context: Context): TriggerMode {
        val modeStr = getPrefs(context).getString(KEY_TRIGGER_MODE, TriggerMode.VOLUME_DOWN_ONLY.name)
        return try {
            TriggerMode.valueOf(modeStr ?: TriggerMode.VOLUME_DOWN_ONLY.name)
        } catch (e: Exception) {
            TriggerMode.VOLUME_DOWN_ONLY
        }
    }

    fun setTriggerMode(context: Context, mode: TriggerMode) {
        getPrefs(context).edit().putString(KEY_TRIGGER_MODE, mode.name).apply()
    }

    fun isVolumeDownEnabled(context: Context): Boolean {
        val mode = getTriggerMode(context)
        return mode == TriggerMode.VOLUME_DOWN_ONLY || mode == TriggerMode.BOTH
    }

    fun isThreeFingerEnabled(context: Context): Boolean {
        val mode = getTriggerMode(context)
        return mode == TriggerMode.THREE_FINGER_SWIPE || mode == TriggerMode.BOTH
    }
}
