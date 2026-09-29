package com.cloud9.gridsync.network

import android.content.Context

/**
 * Stores the names the coach gives to watches (for example "QB Watch").
 * Names are saved per watch ID so they survive reconnects and app restarts.
 */
object WatchNameStore {

    private const val PREFS_NAME = "gridsync_watch_names"
    private const val KEY_PREFIX = "name_"
    const val MAX_NAME_LENGTH = 24

    fun getCustomName(context: Context, watchId: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + watchId, null)
            ?.takeIf { it.isNotBlank() }
    }

    /** A blank name removes the custom name and restores the device name. */
    fun setCustomName(context: Context, watchId: String, name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (clean.isBlank()) {
            editor.remove(KEY_PREFIX + watchId)
        } else {
            editor.putString(KEY_PREFIX + watchId, clean)
        }
        editor.apply()
    }

    fun clearCustomName(context: Context, watchId: String) {
        setCustomName(context, watchId, "")
    }
}
