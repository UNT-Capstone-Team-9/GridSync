package com.cloud9.gridsync.network

data class RoleStatusInfo(
    val role: String,
    val status: String,
    val assignedWatchId: String? = null,
    /** The coach's custom name for the assigned watch, if one was set. */
    val assignedWatchName: String? = null
)
