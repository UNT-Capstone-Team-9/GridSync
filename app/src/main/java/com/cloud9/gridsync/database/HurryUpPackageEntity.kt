package com.cloud9.gridsync.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// A coach-named quick access collection of saved plays. The plays themselves stay in the plays table.
@Entity(tableName = "hurry_up_packages")
data class HurryUpPackageEntity(
    @PrimaryKey(autoGenerate = true)
    val packageId: Long = 0,
    val packageName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
