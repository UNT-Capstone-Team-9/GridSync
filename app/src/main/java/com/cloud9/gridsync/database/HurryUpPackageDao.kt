package com.cloud9.gridsync.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HurryUpPackageDao {

    // Only plays that still exist and are not in the trash count toward a package.
    @Query(
        """
        SELECT p.packageId, p.packageName,
            (SELECT COUNT(*) FROM hurry_up_package_plays r
                INNER JOIN plays pl ON pl.name = r.playName
                WHERE r.packageId = p.packageId AND pl.isDeleted = 0) AS playCount
        FROM hurry_up_packages p
        ORDER BY p.packageName COLLATE NOCASE ASC
        """
    )
    fun getPackageSummaries(): List<HurryUpPackageSummary>

    @Query("SELECT * FROM hurry_up_packages WHERE packageId = :packageId LIMIT 1")
    fun getPackage(packageId: Long): HurryUpPackageEntity?

    @Query("SELECT COUNT(*) FROM hurry_up_packages WHERE packageName = :name COLLATE NOCASE AND packageId != :excludeId")
    fun countPackagesNamed(name: String, excludeId: Long = 0): Int

    @Insert
    fun insertPackage(entity: HurryUpPackageEntity): Long

    @Query("UPDATE hurry_up_packages SET packageName = :name, updatedAt = :updatedAt WHERE packageId = :packageId")
    fun renamePackage(packageId: Long, name: String, updatedAt: Long)

    @Query("UPDATE hurry_up_packages SET updatedAt = :updatedAt WHERE packageId = :packageId")
    fun touchPackage(packageId: Long, updatedAt: Long)

    @Query("DELETE FROM hurry_up_packages WHERE packageId = :packageId")
    fun deletePackageRow(packageId: Long)

    @Query(
        """
        SELECT pl.* FROM plays pl
        INNER JOIN hurry_up_package_plays r ON pl.name = r.playName
        WHERE r.packageId = :packageId AND pl.isDeleted = 0
        ORDER BY r.sortOrder ASC, pl.name COLLATE NOCASE ASC
        """
    )
    fun getPlaysInPackage(packageId: Long): List<PlayEntity>

    @Query("SELECT playName FROM hurry_up_package_plays WHERE packageId = :packageId")
    fun getLinkedPlayNames(packageId: Long): List<String>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM hurry_up_package_plays WHERE packageId = :packageId")
    fun getMaxSortOrder(packageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLinks(links: List<HurryUpPackagePlayCrossRef>)

    @Query("DELETE FROM hurry_up_package_plays WHERE packageId = :packageId AND playName = :playName")
    fun deleteLink(packageId: Long, playName: String)

    @Query("DELETE FROM hurry_up_package_plays WHERE packageId = :packageId")
    fun deleteLinksForPackage(packageId: Long)

    @Query("DELETE FROM hurry_up_package_plays WHERE playName = :playName")
    fun deleteLinksForPlay(playName: String)

    // IGNORE keeps an existing link when the new name is already in that package.
    @Query("UPDATE OR IGNORE hurry_up_package_plays SET playName = :newName WHERE playName = :oldName")
    fun renamePlayInLinks(oldName: String, newName: String)

    @Query("DELETE FROM hurry_up_package_plays WHERE playName NOT IN (SELECT name FROM plays)")
    fun deleteLinksToMissingPlays()
}
