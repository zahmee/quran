package com.mushaf.reader.data.stats

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One completed khatma. Dates are epoch millis; the Hijri/Gregorian labels are derived at
 *  display time. [startedAt] is when that khatma cycle began, [durationDays] its length in days,
 *  and [pagesRead] the pages credited during it. This table is a permanent archive. */
@Entity(tableName = "khatmas")
data class KhatmaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedAt: Long,
    val startedAt: Long,
    val durationDays: Int,
    val pagesRead: Int,
)
