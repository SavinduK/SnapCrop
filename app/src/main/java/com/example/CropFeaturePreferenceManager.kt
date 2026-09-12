package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * CropFeaturePreferenceManager
 *
 * Persists user toggles for crop overlay features:
 * - Share to AI mode (default: true)
 * - Batch select mode (default: true)
 */
object CropFeaturePreferenceManager {
    private const val PREFS_NAME = "snapcrop_feature_prefs"
    private const val KEY_SHARE_TO_AI = "feature_share_to_ai"
    private const val KEY_BATCH_SELECT = "feature_batch_select"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isShareToAiEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHARE_TO_AI, true)
    }

    fun setShareToAiEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHARE_TO_AI, enabled).apply()
    }

    fun isBatchSelectEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BATCH_SELECT, true)
    }

    fun setBatchSelectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BATCH_SELECT, enabled).apply()
    }
}
