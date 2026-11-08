package com.mushaf.reader.data.packs

import android.content.Context
import com.mushaf.reader.data.content.TafsirPassage
import com.mushaf.reader.data.content.TafsirPassageRow
import com.mushaf.reader.data.content.TafsirRowKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a downloaded pack says about one ayah. */
sealed interface PackContent {
    data class Passage(val passage: TafsirPassage) : PackContent
    data class Text(val row: TafsirPassageRow) : PackContent
}

/** Reads the downloaded packs; every call returns null when the pack is not (or no longer) there. */
class PackContentRepository(private val context: Context) {

    suspend fun contentFor(packId: String, kind: PackKind, verseKey: String): PackContent? =
        withContext(Dispatchers.IO) {
            val (sura, ayah) = verseKey.split(':').map { it.toIntOrNull() ?: return@withContext null }
            val key = sura * 1000 + ayah
            when (kind) {
                PackKind.Tafsir -> passage(packId, key, ayah)?.let { PackContent.Passage(it) }
                PackKind.Irab -> text(packId, key, ayah)?.let { PackContent.Text(it) }
            }
        }

    private fun passage(packId: String, key: Int, ayah: Int): TafsirPassage? =
        PackStore.read(context, packId) { db ->
            db.rawQuery(
                """
                SELECT from_ayah, to_ayah, kind, text, notes FROM passage
                WHERE group_id = (SELECT group_id FROM ayah_group WHERE ayah_key = ?)
                ORDER BY row_id
                """.trimIndent(),
                arrayOf(key.toString()),
            ).use { c ->
                val rows = ArrayList<TafsirPassageRow>()
                while (c.moveToNext()) {
                    rows += TafsirPassageRow(
                        kind = TafsirRowKind.fromSource(c.getString(2)),
                        fromAyah = c.getInt(0),
                        toAyah = c.getInt(1),
                        text = c.getString(3),
                        notes = TafsirPassageRow.parseNotes(c.getString(4)),
                    )
                }
                rows.takeIf { it.isNotEmpty() }?.let { TafsirPassage(it, ayah) }
            }
        }

    private fun text(packId: String, key: Int, ayah: Int): TafsirPassageRow? =
        PackStore.read(context, packId) { db ->
            db.rawQuery("SELECT text, notes FROM ayah_text WHERE ayah_key = ?", arrayOf(key.toString())).use { c ->
                if (!c.moveToFirst()) return@use null
                TafsirPassageRow(
                    kind = TafsirRowKind.AyahTafsir,
                    fromAyah = ayah,
                    toAyah = ayah,
                    text = c.getString(0),
                    notes = TafsirPassageRow.parseNotes(c.getString(1)),
                )
            }
        }
}
