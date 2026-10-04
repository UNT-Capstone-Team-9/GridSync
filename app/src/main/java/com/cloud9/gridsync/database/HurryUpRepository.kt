package com.cloud9.gridsync.database

import android.content.Context
import com.cloud9.gridsync.network.PlayMessage
import com.google.gson.Gson

// Hurry-Up package rules on top of Room. Every function blocks, so call them off the main thread.
// Functions that can be refused return an error message, or null when they succeeded.
object HurryUpRepository {

    const val MIN_PLAYS = 2
    const val MIN_PLAYS_MESSAGE = "A Hurry-Up Package must contain at least $MIN_PLAYS plays."
    const val DUPLICATE_PLAY_MESSAGE = "This play is already in this Hurry-Up Package."

    data class AddResult(
        val added: List<String>,
        val alreadyInPackage: List<String>
    )

    private val gson = Gson()

    fun validatePackageName(name: String, nameTaken: Boolean): String? {
        return when {
            name.isBlank() -> "Enter a package name"
            nameTaken -> "A package named \"${name.trim()}\" already exists"
            else -> null
        }
    }

    fun validatePlaySelection(selectedCount: Int): String? {
        return if (selectedCount < MIN_PLAYS) MIN_PLAYS_MESSAGE else null
    }

    fun getSummaries(context: Context): List<HurryUpPackageSummary> {
        val dao = dao(context)
        dao.deleteLinksToMissingPlays()
        return dao.getPackageSummaries()
    }

    fun getPackage(context: Context, packageId: Long): HurryUpPackageEntity? {
        return dao(context).getPackage(packageId)
    }

    fun isNameTaken(context: Context, name: String, excludeId: Long = 0): Boolean {
        return dao(context).countPackagesNamed(name.trim(), excludeId) > 0
    }

    fun createPackage(context: Context, name: String, playNames: List<String>): Long? {
        val cleanName = name.trim()
        val distinctPlays = playNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanName.isBlank() || distinctPlays.size < MIN_PLAYS) return null

        val db = AppDatabase.getDatabase(context)
        var newId: Long? = null

        db.runInTransaction {
            val now = System.currentTimeMillis()
            val id = db.hurryUpPackageDao().insertPackage(
                HurryUpPackageEntity(packageName = cleanName, createdAt = now, updatedAt = now)
            )
            db.hurryUpPackageDao().insertLinks(
                distinctPlays.mapIndexed { index, playName ->
                    HurryUpPackagePlayCrossRef(id, playName, index)
                }
            )
            newId = id
        }

        return newId
    }

    fun renamePackage(context: Context, packageId: Long, newName: String): String? {
        val cleanName = newName.trim()
        validatePackageName(cleanName, isNameTaken(context, cleanName, packageId))?.let { return it }
        dao(context).renamePackage(packageId, cleanName, System.currentTimeMillis())
        return null
    }

    // Removes the package and its links only. Saved plays are never touched.
    fun deletePackage(context: Context, packageId: Long) {
        val db = AppDatabase.getDatabase(context)
        db.runInTransaction {
            db.hurryUpPackageDao().deleteLinksForPackage(packageId)
            db.hurryUpPackageDao().deletePackageRow(packageId)
        }
    }

    fun getPlays(context: Context, packageId: Long): List<PlayMessage> {
        return dao(context).getPlaysInPackage(packageId).mapNotNull { entity ->
            try {
                gson.fromJson(entity.dataJson, PlayMessage::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun addPlays(context: Context, packageId: Long, playNames: List<String>): AddResult {
        val db = AppDatabase.getDatabase(context)
        val dao = db.hurryUpPackageDao()
        val requested = playNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        var result = AddResult(emptyList(), emptyList())

        db.runInTransaction {
            val existing = dao.getLinkedPlayNames(packageId).toSet()
            val toAdd = requested.filter { it !in existing }
            var nextOrder = dao.getMaxSortOrder(packageId) + 1

            dao.insertLinks(toAdd.map { HurryUpPackagePlayCrossRef(packageId, it, nextOrder++) })
            if (toAdd.isNotEmpty()) dao.touchPackage(packageId, System.currentTimeMillis())

            result = AddResult(added = toAdd, alreadyInPackage = requested.filter { it in existing })
        }

        return result
    }

    // Refuses when the package would drop below the minimum, so the caller can offer deleting it instead.
    fun removePlay(context: Context, packageId: Long, playName: String): String? {
        val dao = dao(context)
        if (dao.getPlaysInPackage(packageId).size <= MIN_PLAYS) return MIN_PLAYS_MESSAGE
        dao.deleteLink(packageId, playName)
        dao.touchPackage(packageId, System.currentTimeMillis())
        return null
    }

    // Keeps package links pointing at a play that was saved under a new name.
    fun onPlayRenamed(context: Context, oldName: String, newName: String) {
        if (oldName == newName) return
        val db = AppDatabase.getDatabase(context)
        db.runInTransaction {
            db.hurryUpPackageDao().renamePlayInLinks(oldName, newName)
            db.hurryUpPackageDao().deleteLinksForPlay(oldName)
        }
    }

    fun onPlayPermanentlyDeleted(context: Context, playName: String) {
        dao(context).deleteLinksForPlay(playName)
    }

    fun getLibraryPlayNames(context: Context): List<String> {
        return AppDatabase.getDatabase(context).playDao().getAllPlays().map { it.name }
    }

    private fun dao(context: Context) = AppDatabase.getDatabase(context).hurryUpPackageDao()
}
