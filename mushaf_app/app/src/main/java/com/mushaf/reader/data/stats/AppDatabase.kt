package com.mushaf.reader.data.stats

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class, KhatmaEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun khatmaDao(): KhatmaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: add the khatmas archive table only. The existing sessions table is left
         *  untouched, so every recorded reading session survives the upgrade. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `khatmas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`durationDays` INTEGER NOT NULL, " +
                        "`pagesRead` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quran_reader.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
