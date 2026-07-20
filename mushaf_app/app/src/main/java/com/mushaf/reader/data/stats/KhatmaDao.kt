package com.mushaf.reader.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface KhatmaDao {

    @Insert
    suspend fun insert(khatma: KhatmaEntity): Long

    @Query("SELECT * FROM khatmas ORDER BY completedAt DESC")
    suspend fun all(): List<KhatmaEntity>

    @Query("SELECT COUNT(*) FROM khatmas")
    suspend fun count(): Int
}
