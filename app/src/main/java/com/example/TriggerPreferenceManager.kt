package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * TriggerPreferenceManager
 *
 * Persists the user's screenshot trigger configuration in permanent SharedPreferences.
 * Default is EDGE_PANEL (Screen Edge Bar docked at screen edge).
 */
object TriggerPreferenceManager {
    private const val PREFS_NAME = "snapcrop_trigger_prefs"
    private const val KEY_TRIGGER_MODE = "trigger_mode"

    enum class TriggerMode {
        EDGE_PANEL,             // Default: Screen Edge Panel docked on screen
        POWER_BUTTON_TRIPLE_TAP // 3 times tap of Power Button
    }

    enum class CaptureMode {
        STANDARD,        // Quick Crop: Instant single-screen capture
        LONG_SCREENSHOT  // Long Screenshot: Multi-page scroll capture
    }

    private const val KEY_CAPTURE_MODE = "capture_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTriggerMode(context: Context): TriggerMode {
        val modeStr = getPrefs(context).getString(KEY_TRIGGER_MODE, TriggerMode.EDGE_PANEL.name)
        return when (modeStr) {
            "SLIDER", "EDGE_PANEL" -> TriggerMode.EDGE_PANEL
            "POWER_BUTTON_TRIPLE_TAP", "POWER", "VOLUME" -> TriggerMode.POWER_BUTTON_TRIPLE_TAP
            else -> TriggerMode.EDGE_PANEL
        }
    }

    fun setTriggerMode(context: Context, mode: TriggerMode) {
        getPrefs(context).edit().putString(KEY_TRIGGER_MODE, mode.name).apply()
    }

    fun isEdgePanelEnabled(context: Context): Boolean {
        return getTriggerMode(context) == TriggerMode.EDGE_PANEL
    }

    fun isPowerTripleTapEnabled(context: Context): Boolean {
        return getTriggerMode(context) == TriggerMode.POWER_BUTTON_TRIPLE_TAP
    }

    fun isThreeFingerEnabled(context: Context): Boolean {
        return isEdgePanelEnabled(context)
    }

    fun isVolumeDownEnabled(context: Context): Boolean {
        return isPowerTripleTapEnabled(context)
    }

    fun getCaptureMode(context: Context): CaptureMode {
        val modeStr = getPrefs(context).getString(KEY_CAPTURE_MODE, CaptureMode.STANDARD.name)
        return try {
            CaptureMode.valueOf(modeStr ?: CaptureMode.STANDARD.name)
        } catch (e: Exception) {
            CaptureMode.STANDARD
        }
    }

    fun setCaptureMode(context: Context, mode: CaptureMode) {
        getPrefs(context).edit().putString(KEY_CAPTURE_MODE, mode.name).apply()
    }

    fun isLongScreenshotDefault(context: Context): Boolean {
        return getCaptureMode(context) == CaptureMode.LONG_SCREENSHOT
    }
}


