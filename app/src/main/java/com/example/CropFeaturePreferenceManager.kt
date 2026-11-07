package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * CropFeaturePreferenceManager
 *
 * Persists user toggles for crop overlay features:
 * - Batch select mode (default: true)
 * - Long screenshot scroll capture (default: true)
 */
object CropFeaturePreferenceManager {
    private const val PREFS_NAME = "snapcrop_feature_prefs"
    private const val KEY_BATCH_SELECT = "feature_batch_select"
    private const val KEY_LONG_SCREENSHOT = "feature_long_screenshot"
    private const val KEY_TEXT_RECOGNITION = "feature_text_recognition"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBatchSelectEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BATCH_SELECT, true)
    }

    fun setBatchSelectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BATCH_SELECT, enabled).apply()
    }

    fun isLongScreenshotEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LONG_SCREENSHOT, true)
    }

    fun setLongScreenshotEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LONG_SCREENSHOT, enabled).apply()
    }

    fun isTextRecognitionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TEXT_RECOGNITION, true)
    }

    fun setTextRecognitionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TEXT_RECOGNITION, enabled).apply()
    }
}
