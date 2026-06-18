package com.mushaf.reader.data.stats

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One reading session: when it started/ended and how much was read during it. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val startPage: Int,
    val endPage: Int,
    val pagesRead: Int,
)
