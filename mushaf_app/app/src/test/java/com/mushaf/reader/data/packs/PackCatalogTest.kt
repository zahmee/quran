package com.mushaf.reader.data.packs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PackCatalogTest {

    private fun pack(
        id: String = "tabari",
        kind: String = "tafsir",
        url: String = "https://example.org/tabari-v1.db.gz",
        sha: String = "a".repeat(64),
        schema: Int = 1,
        minApp: Int = 58,
    ) = """
        {"id":"$id","kind":"$kind","version":1,"schema":$schema,"title":"تفسير الطبري","work":"جامع البيان",
         "author":"الطبري","url":"$url","sha256":"$sha","compressedBytes":8180291,
         "installedBytes":41205760,"minAppVersionCode":$minApp}
    """.trimIndent()

    private fun catalog(vararg packs: String, schema: Int = 1) =
        """{"schema":$schema,"packs":[${packs.joinToString(",")}]}"""

    @Test
    fun theShippedCatalogListsTheFourPacks() {
        val text = File("src/main/assets/packs/catalog.json").readText()
        val parsed = PackCatalog.parse(text)
        assertEquals(listOf("ibn_kathir", "tabari", "baghawi", "irab"), parsed.packs.map { it.id })
        assertEquals(PackKind.Irab, parsed.find("irab")?.kind)
        assertTrue(parsed.packs.all { it.url.startsWith("https://github.com/zahmee/natlu-data/releases/download/") })
        assertTrue(parsed.packs.all { it.compressedBytes in 1 until it.installedBytes })
    }

    @Test
    fun aGoodPackIsRead() {
        val info = PackCatalog.parse(catalog(pack())).packs.single()
        assertEquals("tabari", info.id)
        assertEquals(PackKind.Tafsir, info.kind)
        assertEquals(8_180_291L, info.compressedBytes)
        assertEquals(58, info.minAppVersionCode)
    }

    @Test
    fun unsafePacksAreLeftOutButTheRestStays() {
        val parsed = PackCatalog.parse(
            catalog(
                pack(id = "../evil"),
                pack(id = "plain", url = "http://example.org/x.gz"),
                pack(id = "short", sha = "abc"),
                pack(id = "future", kind = "audio"),
                pack(id = "good"),
            )
        )
        assertEquals(listOf("good"), parsed.packs.map { it.id })
    }

    @Test
    fun cleartextIsAllowedOnlyWhenAsked() {
        val text = catalog(pack(url = "http://10.0.2.2:8000/tabari-v1.db.gz"))
        assertTrue(PackCatalog.parse(text).packs.isEmpty())
        assertEquals(1, PackCatalog.parse(text, allowCleartext = true).packs.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNewerCatalogSchemaIsRefused() {
        PackCatalog.parse(catalog(pack(), schema = PackCatalog.SCHEMA + 1))
    }

    @Test
    fun aPackCanAskForANewerApp() {
        val info = PackCatalog.parse(catalog(pack(minApp = 60))).packs.single()
        assertTrue(info.needsNewerApp(58))
        assertFalse(info.needsNewerApp(60))
        val newerFormat = PackCatalog.parse(catalog(pack(schema = PackFormat.SCHEMA + 1))).packs.single()
        assertTrue(newerFormat.needsNewerApp(1000))
    }

    @Test
    fun findReturnsNullForAnUnknownPack() {
        assertNull(PackCatalog.parse(catalog(pack())).find("nothing"))
    }
}
