package com.mushaf.reader.data.content

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContentMetaEntity::class,
        TafsirAyahEntity::class,
        GharibEntryEntity::class,
        GharibAyahMapEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class QuranContentDatabase : RoomDatabase() {

    abstract fun contentDao(): QuranContentDao

    companion object {
        @Volatile
        private var instance: QuranContentDatabase? = null

        fun get(context: Context): QuranContentDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuranContentDatabase::class.java,
                    "quran_content_v1.db",
                )
                    .createFromAsset("databases/quran_content_v1.db")
                    .build()
                    .also { instance = it }
            }
    }
}
