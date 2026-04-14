package com.cloud9.gridsync.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plays")
data class PlayEntity(
    @PrimaryKey
    val name: String,
    val dataJson: String,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)