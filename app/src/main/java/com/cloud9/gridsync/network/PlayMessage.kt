package com.cloud9.gridsync.network
import java.io.Serializable

data class PlayMessage(
    val playName: String,
    val assignments: Map<String, String>,
    val imageResourceName: String
) : Serializable