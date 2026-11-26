package com.mushaf.reader.data.stats

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mushaf.reader.data.notes.NoteDao
import com.mushaf.reader.data.notes.NoteEntity

@Database(
    entities = [SessionEntity::class, KhatmaEntity::class, NoteEntity::class],
    version = 4,
    // Every version's schema is kept in app/schemas; the migration tests open the old ones.
    // Bumping the version means adding a migration, a test for it, and the new schema file.
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun khatmaDao(): KhatmaDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: add the khatmas archive table only. The existing sessions table is left
         *  untouched, so every recorded reading session survives the upgrade. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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

        /** Historical sessions retain their recorded duration; only new sessions exclude pauses. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET durationMs = MAX(endedAt - startedAt, 0)")
            }
        }

        /** v3 → v4: add the reader's notes. Sessions and khatmas are left exactly as they were. */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`surah` INTEGER NOT NULL, " +
                        "`ayah` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`surah`, `ayah`))"
                )
            }
        }

        /** Every migration, oldest first: what [get] opens the database with, and what the
         *  migration tests run, so the two can never disagree. */
        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quran_reader.db"
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }
    }
}
