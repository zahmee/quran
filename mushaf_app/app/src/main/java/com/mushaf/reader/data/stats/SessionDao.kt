package com.mushaf.reader.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM sessions")
    suspend fun totalDurationMs(): Long

    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM sessions")
    suspend fun totalPagesRead(): Int

    @Query("SELECT * FROM sessions ORDER BY id DESC LIMIT 1")
    suspend fun lastSession(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun allSessions(): List<SessionEntity>
}
