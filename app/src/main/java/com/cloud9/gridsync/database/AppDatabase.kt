package com.cloud9.gridsync.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlayEntity::class,
        HurryUpPackageEntity::class,
        HurryUpPackagePlayCrossRef::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playDao(): PlayDao

    abstract fun hurryUpPackageDao(): HurryUpPackageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Adds the Hurry-Up tables without touching the saved plays.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hurry_up_packages` (" +
                        "`packageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hurry_up_package_plays` (" +
                        "`packageId` INTEGER NOT NULL, " +
                        "`playName` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`packageId`, `playName`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_hurry_up_package_plays_playName` " +
                        "ON `hurry_up_package_plays` (`playName`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gridsync_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    // Only versions older than 3 have no migration path and may still be rebuilt.
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
