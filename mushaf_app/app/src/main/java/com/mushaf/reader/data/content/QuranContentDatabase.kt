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
        TafsirPassageEntity::class,
        TafsirAyahGroupEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class QuranContentDatabase : RoomDatabase() {

    abstract fun contentDao(): QuranContentDao

    companion object {
        /**
         * The file name changes with every new content asset. Room copies a prepackaged database
         * only when the file is missing, so a new name is what makes an existing install pick up
         * new content; the copy under the old name is then deleted rather than left on the device.
         */
        private const val DATABASE_NAME = "quran_content_v2.db"
        private val RETIRED_DATABASE_NAMES = listOf("quran_content_v1.db")

        @Volatile
        private var instance: QuranContentDatabase? = null

        fun get(context: Context): QuranContentDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    RETIRED_DATABASE_NAMES.forEach { appContext.deleteDatabase(it) }
                    Room.databaseBuilder(
                        appContext,
                        QuranContentDatabase::class.java,
                        DATABASE_NAME,
                    )
                        .createFromAsset("databases/$DATABASE_NAME")
                        .build()
                }.also { instance = it }
            }
    }
}
