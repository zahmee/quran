package com.mushaf.reader.data.stats

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mushaf.reader.data.notes.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every upgrade path a reader's database can take, run on real SQLite from the schemas in
 * app/schemas. Each test fills an old version with data, migrates it, lets Room check the result
 * against the current schema, and then reads the data back: a reader's sessions, khatmas and
 * notes must all survive.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun v1_to_latest_keepsSessions_andTimesThemFromTheirDates() {
        helper.createDatabase(DB, 1).use { db ->
            db.insertSession(id = 1, startedAt = 1_000, endedAt = 61_000, pages = 3)
            // A clock that moved backwards mid-session: its time must not go negative.
            db.insertSession(id = 2, startedAt = 90_000, endedAt = 80_000, pages = 1)
        }

        helper.runMigrationsAndValidate(DB, LATEST, true, *AppDatabase.MIGRATIONS).close()

        withRoom { room ->
            val sessions = room.sessionDao().allSessions().associateBy { it.id }
            assertEquals(2, sessions.size)
            assertEquals(60_000L, sessions.getValue(1).durationMs)
            assertEquals(3, sessions.getValue(1).pagesRead)
            assertEquals(0L, sessions.getValue(2).durationMs)
            assertEquals(0, room.khatmaDao().count())
            assertEquals(0, room.noteDao().all().size)
        }
    }

    @Test
    fun v2_to_latest_keepsSessionsAndKhatmas() {
        helper.createDatabase(DB, 2).use { db ->
            db.insertSession(id = 7, startedAt = 5_000, endedAt = 125_000, pages = 4)
            db.insertKhatma(id = 1, completedAt = 2_000_000, startedAt = 1_000_000, days = 30, pages = 604)
        }

        helper.runMigrationsAndValidate(DB, LATEST, true, *AppDatabase.MIGRATIONS).close()

        withRoom { room ->
            assertEquals(120_000L, room.sessionDao().get(7)?.durationMs)
            val khatma = room.khatmaDao().all().single()
            assertEquals(KhatmaEntity(1, 2_000_000, 1_000_000, 30, 604), khatma)
        }
    }

    @Test
    fun v3_to_latest_keepsRecordedDurations_andAcceptsNotes() {
        helper.createDatabase(DB, 3).use { db ->
            // From v3 on a session's time excludes pauses, so it is less than the span of its
            // dates; the upgrade must keep it, not recompute it.
            db.insertSession(id = 3, startedAt = 0, endedAt = 600_000, pages = 5, durationMs = 240_000)
            db.insertKhatma(id = 2, completedAt = 9_000_000, startedAt = 4_000_000, days = 12, pages = 610)
        }

        helper.runMigrationsAndValidate(DB, LATEST, true, AppDatabase.MIGRATION_3_4).close()

        withRoom { room ->
            assertEquals(240_000L, room.sessionDao().get(3)?.durationMs)
            assertEquals(610, room.khatmaDao().all().single().pagesRead)

            val note = NoteEntity(surah = 2, ayah = 255, text = "آية الكرسي", createdAt = 1, updatedAt = 2)
            room.noteDao().upsert(note)
            assertEquals(note, room.noteDao().find(2, 255))
        }
    }

    /** Opens the migrated file the way the app does, so Room's own schema check runs too. */
    private fun withRoom(block: suspend (AppDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            runBlocking { block(room) }
        } finally {
            room.close()
        }
    }

    private fun SupportSQLiteDatabase.insertSession(
        id: Long,
        startedAt: Long,
        endedAt: Long,
        pages: Int,
        durationMs: Long? = null,
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("startedAt", startedAt)
            put("endedAt", endedAt)
            put("startPage", 1)
            put("endPage", pages)
            put("pagesRead", pages)
            if (durationMs != null) put("durationMs", durationMs)
        }
        insert("sessions", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun SupportSQLiteDatabase.insertKhatma(
        id: Long,
        completedAt: Long,
        startedAt: Long,
        days: Int,
        pages: Int,
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("completedAt", completedAt)
            put("startedAt", startedAt)
            put("durationDays", days)
            put("pagesRead", pages)
        }
        insert("khatmas", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private companion object {
        const val DB = "migration-test.db"
        const val LATEST = 4
    }
}
