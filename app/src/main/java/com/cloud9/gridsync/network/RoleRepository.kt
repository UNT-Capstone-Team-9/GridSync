package com.cloud9.gridsync.network

import android.content.Context

object RoleRepository {

    private const val PREFS_NAME = "gridsync_roles"
    private const val KEY_ROLES = "roles_csv"

    private val defaultRoles = listOf(
        "QB",
        "RB",
        "WR1",
        "WR2",
        "TE",
        "LT",
        "LG",
        "C",
        "RG",
        "RT",
        "FB"
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