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
 * The Tafsir Center's page fawaed, surah introductions and tajweed notes: a read-only database of its own, built by
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

/** A tajweed note; a run of ayahs the source gives one note shares one row. */
@Entity(tableName = "ayah_tajweed", primaryKeys = ["surah_number", "from_ayah"])
data class AyahTajweedEntity(
    @ColumnInfo(name = "surah_number") val surahNumber: Int,
    @ColumnInfo(name = "from_ayah") val fromAyah: Int,
    @ColumnInfo(name = "to_ayah") val toAyah: Int,
    /** Quran words in braces; one paragraph per line, a list item opening with «•». */
    @ColumnInfo(name = "text") val text: String,
)

/** A surah's counts and curiosities, from the Center's statistics. */
@Entity(tableName = "surah_stats")
data class SurahStatsEntity(
    @PrimaryKey @ColumnInfo(name = "surah_number") val surahNumber: Int,
    /** How the surah opens: «قسم»، «نداء»، «حروف مقطعة»… */
    @ColumnInfo(name = "begin_type") val beginType: String,
    /** What the surah's name is drawn from; empty when the source gives nothing. */
    @ColumnInfo(name = "name_topic") val nameTopic: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "char_count") val charCount: Int,
    /** Every ayah whose text recurs in the surah, comma-separated, in order; empty for none. */
    @ColumnInfo(name = "repeated_ayahs") val repeatedAyahs: String,
    /** One per line; empty when too many words tie to be worth naming. */
    @ColumnInfo(name = "longest_words") val longestWords: String,
    /** Letters of the alphabet that never occur in the surah, run together. */
    @ColumnInfo(name = "missing_letters") val missingLetters: String,
)

/** A root of the Quran's words, with everything a search by it shows; see [com.mushaf.reader.data.RootSearch]. */
@Entity(tableName = "root")
data class RootEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "root") val root: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "ayah_count") val ayahCount: Int,
    /** One per line, «form<TAB>count», most frequent first. */
    @ColumnInfo(name = "forms") val forms: String,
    /** Space-separated «ayahKey:word:form» in mushaf order, word and form counted from 0. */
    @ColumnInfo(name = "occurrences") val occurrences: String,
)

/** A root without its occurrences: what matching a typed word needs. */
data class RootIndexRow(
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "root") val root: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "ayah_count") val ayahCount: Int,
    @ColumnInfo(name = "forms") val forms: String,
)

@Dao
interface CenterExtrasDao {
    @Query("SELECT text FROM page_fawaed WHERE page = :page ORDER BY position")
    suspend fun fawaedForPage(page: Int): List<String>

    @Query("SELECT * FROM surah_info WHERE surah_number = :surah LIMIT 1")
    suspend fun surahInfo(surah: Int): SurahInfoEntity?

    @Query("SELECT * FROM surah_section WHERE surah_number = :surah ORDER BY position")
    suspend fun surahSections(surah: Int): List<SurahSectionEntity>

    @Query(
        "SELECT * FROM ayah_tajweed WHERE surah_number = :surah AND from_ayah <= :ayah AND to_ayah >= :ayah LIMIT 1"
    )
    suspend fun tajweed(surah: Int, ayah: Int): AyahTajweedEntity?

    @Query("SELECT * FROM surah_stats WHERE surah_number = :surah LIMIT 1")
    suspend fun surahStats(surah: Int): SurahStatsEntity?

    @Query("SELECT id, root, word_count, ayah_count, forms FROM root ORDER BY id")
    suspend fun rootIndex(): List<RootIndexRow>

    @Query("SELECT occurrences FROM root WHERE id = :id LIMIT 1")
    suspend fun rootOccurrences(id: Int): String?
}

@Database(
    entities = [
        CenterExtrasMetaEntity::class,
        PageFaidahEntity::class,
        SurahInfoEntity::class,
        SurahSectionEntity::class,
        AyahTajweedEntity::class,
        SurahStatsEntity::class,
        RootEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class CenterExtrasDatabase : RoomDatabase() {

    abstract fun dao(): CenterExtrasDao

    companion object {
        /** Like quran_content_v4.db: a new asset gets a new name, so installs copy it afresh. */
        private const val DATABASE_NAME = "center_extras_v4.db"
        private val RETIRED_DATABASE_NAMES =
            listOf("center_extras_v1.db", "center_extras_v2.db", "center_extras_v3.db")

        @Volatile
        private var instance: CenterExtrasDatabase? = null

        fun get(context: Context): CenterExtrasDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext.also { app ->
                        RETIRED_DATABASE_NAMES.forEach { app.deleteDatabase(it) }
                    },
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
    /** How the surah opens, e.g. «قسم»; null when the statistics are missing. */
    val beginType: String? = null,
    val wordCount: Int? = null,
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

    /** «كلمة واحدة»، «كلمتان»، «١٠ كلمات»، «٢٩ كلمة». */
    fun words(count: Int): String = when {
        count == 1 -> "كلمة واحدة"
        count == 2 -> "كلمتان"
        count % 100 in 3..10 -> "${count.arabicDigits()} كلمات"
        else -> "${count.arabicDigits()} كلمة"
    }

    /** «تبدأ بقسم»، «تبدأ بحروف مقطعة». */
    fun opening(beginType: String): String = "تبدأ ب$beginType"

    /** The «إحصاءات» section's lines, one fact each; facts the source lacks are left out. */
    fun statsLines(stats: SurahStatsEntity): List<String> = buildList {
        add("• عدد حروفها: ${stats.charCount.arabicDigits()}، وعدد كلماتها: ${stats.wordCount.arabicDigits()}.")
        if (stats.nameTopic.isNotBlank()) add("• موضوع اسمها: ${stats.nameTopic}.")
        val repeated = stats.repeatedAyahs.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (repeated.isNotEmpty()) {
            add("• آيات يتكرر نصها فيها: ${repeated.joinToString("، ") { it.arabicDigits() }}.")
        }
        val longest = stats.longestWords.lines().filter { it.isNotBlank() }
        if (longest.isNotEmpty()) {
            add("• أطول كلماتها: ${longest.joinToString("، ") { "{$it}" }}.")
        }
        if (stats.missingLetters.isNotBlank()) {
            add("• حروف هجاء لم ترد فيها: ${stats.missingLetters.toList().joinToString(" ")}.")
        }
    }

    const val STATS_TITLE = "إحصاءات"

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

/** How to recite an ayah: a note that may cover the ayahs around it too. */
data class TajweedNote(val fromAyah: Int, val toAyah: Int, val text: String)

class CenterExtrasRepository(context: Context) {
    private val dao = CenterExtrasDatabase.get(context).dao()

    /** The page's fawaed in the Mukhtasar's order; empty for a page outside 1..604. */
    suspend fun fawaedForPage(page: Int): List<String> = dao.fawaedForPage(page)

    /** Every root, without its occurrences, for matching typed words against. */
    suspend fun rootIndex(): List<RootIndexRow> = dao.rootIndex()

    /** The packed occurrences of root [id]; see [RootEntity.occurrences]. */
    suspend fun rootOccurrences(id: Int): String? = dao.rootOccurrences(id)

    /** The tajweed note on [ayah] of [surah]; null for the few ayahs the source has none for. */
    suspend fun tajweed(surah: Int, ayah: Int): TajweedNote? =
        dao.tajweed(surah, ayah)?.let { TajweedNote(it.fromAyah, it.toAyah, it.text) }

    suspend fun surahCard(surah: Int): SurahCard? {
        val info = dao.surahInfo(surah) ?: return null
        val stats = dao.surahStats(surah)
        val statsSection = stats?.let {
            SurahCardSection(
                title = SurahCardText.STATS_TITLE,
                row = TafsirPassageRow(
                    kind = TafsirRowKind.Prose,
                    fromAyah = 0,
                    toAyah = 0,
                    text = SurahCardText.statsLines(it).joinToString("\n"),
                ),
            )
        }
        return SurahCard(
            beginType = stats?.beginType?.takeIf { it.isNotBlank() },
            wordCount = stats?.wordCount,
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
            } + listOfNotNull(statsSection),
        )
    }
}
