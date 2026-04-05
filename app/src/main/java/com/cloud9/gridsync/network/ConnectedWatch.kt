package com.cloud9.gridsync.network

data class ConnectedWatch(
    val watchId: String,
    val watchName: String,
    val ipAddress: String,
    val role: String?
)