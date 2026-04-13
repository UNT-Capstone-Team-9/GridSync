package com.cloud9.gridsync.network
import java.io.Serializable

data class PlayMessage(
    val playName: String,
    val assignment: String,
    val imageResourceName: String,
    val role: String = ""
) : Serializable