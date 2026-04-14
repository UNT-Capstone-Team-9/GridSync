package com.cloud9.gridsync.database

import android.content.Context
import com.cloud9.gridsync.network.PlayMessage
import com.google.gson.Gson

object DefaultPlaySeeder {

    private val gson = Gson()

    private val defaultPlayNames = listOf(
        "RED SLOT RIGHT BOOTLEG LEFT",
        "ACE 60 SLANT RETURNS",
        "ACE FLORIDA",
        "BLUE PONY LEFT BAYLOR",
        "ACE 66",
        "ACE 97",
        "TRAIN A JET REBELS",
        "RED WING RIGHT BOOTLEG RIGHT"
    )

    private val defaultRoles = listOf(
        "QB",
        "RB",
        "WR1",
        "WR2",
        "TE",
        "LT",
        "LG",
        "C",
        "RG",
        "RT",
        "FB"
    )

    fun seedDefaultsIfMissing(context: Context) {
        val dao = AppDatabase.getDatabase(context).playDao()

        buildDefaultPlayEntities().forEach { playEntity ->
            val existing = dao.getPlayByName(playEntity.name)
            if (existing == null) {
                dao.insertPlay(playEntity)
            }
        }
    }

    private fun buildDefaultPlayEntities(): List<PlayEntity> {
        return defaultPlayNames.map { playName ->
            val assignments = defaultRoles.associateWith {
                "Built in default play. Route drawing for this play has not been digitized yet."
            }

            val playMessage = PlayMessage(
                playName = playName,
                assignments = assignments,
                movements = emptyMap(),
                imageResourceName = ""
            )

            PlayEntity(
                name = playName,
                dataJson = gson.toJson(playMessage)
            )
        }
    }
}