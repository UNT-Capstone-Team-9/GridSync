package com.cloud9.gridsync.database

import android.content.Context
import com.cloud9.gridsync.network.PlayMessage
import com.google.gson.Gson

object DefaultPlaySeeder {

    private val gson = Gson()

    private const val PLACEHOLDER_ASSIGNMENT =
        "Built in default play. Route drawing for this play has not been digitized yet."

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

    // The built in placeholder plays are no longer added to the library. This removes copies
    // left by earlier versions, but only while they still hold just the placeholder data, so a
    // default play the coach has since edited is kept.
    fun removeUntouchedDefaults(context: Context) {
        val dao = AppDatabase.getDatabase(context).playDao()

        defaultPlayNames.forEach { playName ->
            val existing = dao.getPlayByName(playName) ?: return@forEach
            if (isUntouchedDefault(existing.dataJson)) {
                dao.permanentlyDeleteByName(playName)
                HurryUpRepository.onPlayPermanentlyDeleted(context, playName)
            }
        }
    }

    // Compares content rather than raw JSON, since field order in the stored JSON can vary by device.
    private fun isUntouchedDefault(dataJson: String): Boolean {
        val play = try {
            gson.fromJson(dataJson, PlayMessage::class.java)
        } catch (_: Exception) {
            return false
        } ?: return false

        return play.players.isNullOrEmpty() &&
            play.movements.isNullOrEmpty() &&
            play.formationName.isNullOrBlank() &&
            play.assignments.orEmpty().values.all { it == PLACEHOLDER_ASSIGNMENT }
    }
}
