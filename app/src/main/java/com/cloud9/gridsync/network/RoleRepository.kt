package com.cloud9.gridsync.network

import android.content.Context

object RoleRepository {

    private const val PREFS_NAME = "gridsync_roles"
    private const val KEY_ROLES = "roles_csv"

    // Every player label the play designer can put on the field, so each one can have a watch.
    // Duplicate positions use distinct labels (TE and TE2, RB and RB2).
    private val defaultRoles = listOf(
        "QB",
        "RB",
        "RB2",
        "FB",
        "HB",
        "WR1",
        "WR2",
        "WR3",
        "WR4",
        "TE",
        "TE2",
        "LT",
        "LG",
        "C",
        "RG",
        "RT",
        "OL"
    )

    fun getRoles(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ROLES, null)

        if (saved.isNullOrBlank()) {
            return defaultRoles.toMutableList()
        }

        return saved.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
    }

    fun saveRoles(context: Context, roles: List<String>) {
        val cleanRoles = roles
            .map { it.trim() }
            .filter { it.isNotBlank() }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLES, cleanRoles.joinToString("|"))
            .apply()
    }

    fun resetRoles(context: Context) {
        saveRoles(context, defaultRoles)
    }
}