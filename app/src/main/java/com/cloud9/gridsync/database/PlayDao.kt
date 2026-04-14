package com.cloud9.gridsync.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayDao {

    @Query("SELECT * FROM plays WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlays(): List<PlayEntity>

    @Query("SELECT * FROM plays WHERE isDeleted = 0 ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    fun getAllPlaysNewestFirst(): List<PlayEntity>

    @Query("SELECT * FROM plays WHERE isDeleted = 0 ORDER BY updatedAt ASC, name COLLATE NOCASE ASC")
    fun getAllPlaysOldestFirst(): List<PlayEntity>

    @Query("SELECT * FROM plays WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedPlays(): List<PlayEntity>

    @Query("SELECT COUNT(*) FROM plays WHERE isDeleted = 0")
    fun getPlayCount(): Int

    @Query("SELECT * FROM plays WHERE name = :name LIMIT 1")
    fun getPlayByName(name: String): PlayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlay(play: PlayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlays(plays: List<PlayEntity>)

    @Query("UPDATE plays SET isDeleted = 1, deletedAt = :deletedAt WHERE name = :name")
    fun moveToTrash(name: String, deletedAt: Long)

    @Query("UPDATE plays SET isDeleted = 0, deletedAt = NULL WHERE name = :name")
    fun restorePlay(name: String)

    @Query("DELETE FROM plays WHERE name = :name")
    fun permanentlyDeleteByName(name: String)

    @Query("DELETE FROM plays WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < :cutoffTime")
    fun deleteExpiredTrash(cutoffTime: Long)
}