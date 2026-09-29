package com.cloud9.gridsync.network

data class ConnectedWatch(
    val watchId: String,
    /** Name shown in the app: the coach's custom name if set, otherwise the device name. */
    val watchName: String,
    val ipAddress: String,
    val role: String?,
    /** Name the watch reported about itself (for example "SPRD LOKMAT APPLD % MAX"). */
    val deviceName: String = watchName
)
