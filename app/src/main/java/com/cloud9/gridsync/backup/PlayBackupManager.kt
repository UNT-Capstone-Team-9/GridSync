package com.cloud9.gridsync.backup

import android.content.Context
import android.net.Uri
import com.cloud9.gridsync.database.AppDatabase
import com.cloud9.gridsync.database.PlayEntity
import com.cloud9.gridsync.network.PlayMessage
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object PlayBackupManager {

    private val gson = Gson()

    fun exportActivePlays(context: Context, uri: Uri): Int {
        val dao = AppDatabase.getDatabase(context).playDao()
        val activeEntities = dao.getAllPlays()

        val plays = activeEntities.mapNotNull { entity ->
            try {
                gson.fromJson(entity.dataJson, PlayMessage::class.java)
            } catch (_: Exception) {
                null
            }
        }

        val backupFile = PlayBackupFile(
            plays = plays
        )

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(gson.toJson(backupFile))
                writer.flush()
            }
        } ?: throw IllegalStateException("Could not open export destination")

        return plays.size
    }

    fun importPlays(context: Context, uri: Uri): Int {
        val jsonText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalStateException("Could not open import file")

        val backupFile = gson.fromJson(jsonText, PlayBackupFile::class.java)
            ?: throw IllegalStateException("Invalid backup file")

        val dao = AppDatabase.getDatabase(context).playDao()

        var importedCount = 0

        backupFile.plays.forEach { play ->
            val entity = PlayEntity(
                name = play.playName.trim(),
                dataJson = gson.toJson(play),
                isDeleted = false,
                deletedAt = null
            )
            dao.insertPlay(entity)
            importedCount++
        }

        return importedCount
    }
}