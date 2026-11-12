package com.mushaf.reader.data.content

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * The Tafsir Center's page fawaed and surah introductions: a read-only database of its own, built by
 * tools/build_center_extras.py. It is apart from quran_content_v4.db so that adding it does not make
 * every install copy that larger file again.
 */

@Entity(tableName = "content_meta")
data class CenterExtrasMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

@Entity(tableName = "page_fawaed", primaryKeys = ["page", "position"])
data class PageFaidahEntity(
    @ColumnInfo(name = "page") val page: Int,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "text") val text: String,
)

@Entity(tableName = "surah_info")
data class SurahInfoEntity(
    @PrimaryKey @ColumnInfo(name = "surah_number") val surahNumber: Int,
    /** «مكية» or «مدنية». */
    @ColumnInfo(name = "revelation_place") val revelationPlace: String,
    @ColumnInfo(name = "revelation_order") val revelationOrder: Int,
    @ColumnInfo(name = "surah_class") val surahClass: String,
    /** The ayahs with a prostration, comma-separated; empty for none. */
    @ColumnInfo(name = "sajdah_ayahs") val sajdahAyahs: String,
    @ColumnInfo(name = "goal") val goal: String,
)

@Entity(tableName = "surah_section", primaryKeys = ["surah_number", "position"])
data class SurahSectionEntity(
    @ColumnInfo(name = "surah_number") val surahNumber: Int,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "title") val title: String,
    /** May hold note anchors ⟦n⟧, and sub-headings as whole lines wrapped in asterisks. */
    @ColumnInfo(name = "text") val text: String,
    /** One per line, in anchor order. */
    @ColumnInfo(name = "notes") val notes: String,
)

@Dao
interface CenterExtrasDao {
    @Query("SELECT text FROM page_fawaed WHERE page = :page ORDER BY position")
    suspend fun fawaedForPage(page: Int): List<String>

    @Query("SELECT * FROM surah_info WHERE surah_number = :surah LIMIT 1")
    suspend fun surahInfo(surah: Int): SurahInfoEntity?

    @Query("SELECT * FROM surah_section WHERE surah_number = :surah ORDER BY position")
    suspend fun surahSections(surah: Int): List<SurahSectionEntity>
}

@Database(
    entities = [
        CenterExtrasMetaEntity::class,
        PageFaidahEntity::class,
        SurahInfoEntity::class,
        SurahSectionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CenterExtrasDatabase : RoomDatabase() {

    abstract fun dao(): CenterExtrasDao

    companion object {
        /** Like quran_content_v4.db: a new asset gets a new name, so installs copy it afresh. */
        private const val DATABASE_NAME = "center_extras_v1.db"

        @Volatile
        private var instance: CenterExtrasDatabase? = null

        fun get(context: Context): CenterExtrasDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CenterExtrasDatabase::class.java,
                    DATABASE_NAME,
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .build()
                    .also { instance = it }
            }
    }
}

/** One part of a surah's introduction: its names, its revelation, its merits, the count of its ayahs. */
data class SurahCardSection(
    val title: String,
    /** One row per paragraph group; see [SurahCardText] for the markup a row may carry. */
    val row: TafsirPassageRow,
)

data class SurahCard(
    val surah: Int,
    val revelationPlace: String,
    val revelationOrder: Int,
    val surahClass: String,
    val sajdahAyahs: List<Int>,
    val goal: String,
    val sections: List<SurahCardSection>,
) {
    /** The short facts as they are copied or shared: the long sections stay in the app. */
    fun summary(surahName: String, ayahCount: Int): String = buildString {
        append("سورة $surahName: ${revelationPlace}، ${SurahCardText.ayahs(ayahCount)}، ")
        append("ترتيبها في النزول ${revelationOrder.arabicDigits()}، ومن ${surahClass}.")
        if (sajdahAyahs.isNotEmpty()) {
            append("\nفيها سجدة تلاوة: ${SurahCardText.sajdahPlaces(sajdahAyahs)}.")
        }
        append("\nمقصدها: $goal")
    }
}

/** How the surah card writes its facts in Arabic. */
object SurahCardText {
    /** «آية واحدة»، «آيتان»، «٣ آيات»، «١١ آية»… as Arabic counts nouns. */
    fun ayahs(count: Int): String = when {
        count == 1 -> "آية واحدة"
        count == 2 -> "آيتان"
        count % 100 in 3..10 -> "${count.arabicDigits()} آيات"
        else -> "${count.arabicDigits()} آية"
    }

    /** «الآية ١٨، والآية ٧٧». */
    fun sajdahPlaces(ayahs: List<Int>): String =
        ayahs.joinToString("، و") { "الآية ${it.arabicDigits()}" }

    fun parseSajdah(column: String): List<Int> =
        column.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** A whole line wrapped in asterisks: a sub-heading of the section. Group 1 is its words. */
    val SubHeading = Regex("^\\*([^*\\n]+)\\*$")

    /** A line the source opens with a star: a point the text goes on to argue, shown in bold. */
    val StarredLine = Regex("^\\*\\s+(?!\\*)(.+)$")

    /** «* * *»: the source's own break between parts. */
    val Divider = Regex("^\\*(?:\\s*\\*)+$")
}

private val ArabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

private fun Int.arabicDigits(): String = toString().map { if (it in '0'..'9') ArabicDigits[it - '0'] else it }.joinToString("")

class CenterExtrasRepository(context: Context) {
    private val dao = CenterExtrasDatabase.get(context).dao()

    /** The page's fawaed in the Mukhtasar's order; empty for a page outside 1..604. */
    suspend fun fawaedForPage(page: Int): List<String> = dao.fawaedForPage(page)

    suspend fun surahCard(surah: Int): SurahCard? {
        val info = dao.surahInfo(surah) ?: return null
        return SurahCard(
            surah = surah,
            revelationPlace = info.revelationPlace,
            revelationOrder = info.revelationOrder,
            surahClass = info.surahClass,
            sajdahAyahs = SurahCardText.parseSajdah(info.sajdahAyahs),
            goal = info.goal,
            sections = dao.surahSections(surah).map { section ->
                SurahCardSection(
                    title = section.title,
                    row = TafsirPassageRow(
                        kind = TafsirRowKind.Prose,
                        fromAyah = 0,
                        toAyah = 0,
                        text = section.text,
                        notes = TafsirPassageRow.parseNotes(section.notes),
                    ),
                )
            },
        )
    }
}
