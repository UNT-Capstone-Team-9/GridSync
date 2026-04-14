package com.cloud9.gridsync.network

data class RoleStatusInfo(
    val role: String,
    val status: String,
    val assignedWatchId: String? = null
)