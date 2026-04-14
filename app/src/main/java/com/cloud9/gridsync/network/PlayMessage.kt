package com.cloud9.gridsync.network

import java.io.Serializable

data class PlayMessage(
    val playName: String,
    val assignments: Map<String, String>,
    val movements: Map<String, List<PointData>>, // This is what was missing!
    val imageResourceName: String = ""
) : Serializable