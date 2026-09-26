package com.cloud9.gridsync.network

import java.io.Serializable

// One offensive player in a play. The id is unique (te_1, te_2) while positionType can repeat.
// displayLabel is also unique and is the role name used for watch assignments (TE, TE2).
// x and y are normalized 0 to 1 across the field and are null while the player is on the bench.
data class PlayerPosition(
    val id: String,
    val positionType: String,
    val displayLabel: String,
    val isActive: Boolean,
    val x: Float?,
    val y: Float?,
    val assignmentType: String? = "",
    val instruction: String? = ""
) : Serializable
