package com.cloud9.gridsync.network

import java.io.Serializable

// Plays saved before the formation designer only have playName, assignments and movements,
// with both maps keyed by role label. Gson leaves missing fields null, so the newer fields are
// nullable. When players is present, movements are keyed by player id and assignments by
// displayLabel (the watch role). routeOptions holds each player's dashed option branches, keyed
// the same way as movements; plays saved before option routes leave it null.
data class PlayMessage(
    val playName: String,
    val assignments: Map<String, String>,
    val movements: Map<String, List<PointData>>,
    val imageResourceName: String = "",
    val formationName: String? = null,
    val playType: String? = null,
    val players: List<PlayerPosition>? = null,
    val routeOptions: Map<String, List<RouteBranch>>? = null
) : Serializable
