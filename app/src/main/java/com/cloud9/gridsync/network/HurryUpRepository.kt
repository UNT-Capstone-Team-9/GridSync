package com.cloud9.gridsync.network

import android.content.Context
import org.json.JSONArray

/**
 * Stores the coach's Hurry-Up Play Package: an ordered list of play names
 * picked from the Play Library, kept on the tablet for quick access.
 */
object HurryUpRepository {

    private const val PREFS_NAME = "gridsync_hurry_up"
    private const val KEY_PLAY_NAMES = "play_names_json"

    fun getPlayNames(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAY_NAMES, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { array.getString(it) }
                .filter { it.isNotBlank() }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setPlayNames(context: Context, names: List<String>) {
        val array = JSONArray()
        names.filter { it.isNotBlank() }.distinct().forEach { array.put(it) }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAY_NAMES, array.toString())
            .apply()
    }

    fun removePlay(context: Context, name: String) {
        setPlayNames(context, getPlayNames(context).filter { it != name })
    }
}
