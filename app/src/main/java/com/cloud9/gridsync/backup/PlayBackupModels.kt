package com.cloud9.gridsync.backup

import com.cloud9.gridsync.network.PlayMessage

data class PlayBackupFile(
    val appName: String = "GridSync",
    val exportedAt: Long = System.currentTimeMillis(),
    val plays: List<PlayMessage> = emptyList()
)
