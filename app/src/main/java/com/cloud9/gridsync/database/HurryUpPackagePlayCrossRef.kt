package com.cloud9.gridsync.database

import androidx.room.Entity
import androidx.room.Index

// Links a package to a saved play by the play's primary key (its name). The composite key stops the
// same play appearing twice in one package while letting it belong to any number of packages.
// There is no foreign key on purpose: saving an edited play replaces its row, which would cascade
// and wipe these links. Stale links are filtered out when read and cleaned up on delete.
@Entity(
    tableName = "hurry_up_package_plays",
    primaryKeys = ["packageId", "playName"],
    indices = [Index("playName")]
)
data class HurryUpPackagePlayCrossRef(
    val packageId: Long,
    val playName: String,
    val sortOrder: Int
)
