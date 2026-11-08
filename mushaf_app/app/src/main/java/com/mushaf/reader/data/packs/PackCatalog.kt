package com.mushaf.reader.data.packs

import org.json.JSONObject

/** What a downloadable pack holds, which decides how the explanation sheet shows it. */
enum class PackKind(val id: String) {
    /** A tafsir laid out in passages, like the bundled Sa'di. */
    Tafsir("tafsir"),

    /** One text per ayah: the i'rab. */
    Irab("irab"),
    ;

    companion object {
        fun fromId(id: String?): PackKind? = entries.firstOrNull { it.id == id }
    }
}

/** The pack file format this app reads; a pack of a newer schema needs a newer app. */
object PackFormat {
    const val SCHEMA = 1
}

/** One pack as the published catalog describes it, before it is downloaded. */
data class PackInfo(
    /** Stable, and the pack's file name on the device: [a-z0-9_]. */
    val id: String,
    val kind: PackKind,
    val version: Int,
    val schema: Int,
    /** A short name for a tab or chip, e.g. «تفسير ابن كثير». */
    val title: String,
    /** The book's full name. */
    val work: String,
    val author: String,
    val url: String,
    /** Of the compressed file at [url]. */
    val sha256: String,
    val compressedBytes: Long,
    /** The pack's size on the device once unpacked. */
    val installedBytes: Long,
    val minAppVersionCode: Int,
) {
    fun needsNewerApp(appVersionCode: Int): Boolean =
        appVersionCode < minAppVersionCode || schema > PackFormat.SCHEMA
}

data class PackCatalog(val packs: List<PackInfo>) {

    fun find(id: String): PackInfo? = packs.firstOrNull { it.id == id }

    companion object {
        const val SCHEMA = 1
        private val Id = Regex("[a-z0-9_]{1,40}")
        private val Sha256 = Regex("[0-9a-f]{64}")

        /**
         * Reads the catalog JSON. A pack this app cannot use safely — an unknown kind, an id that
         * would not make a plain file name, a URL that is not HTTPS — is left out rather than
         * failing the whole list. [allowCleartext] is for debug builds pointed at a local server.
         */
        fun parse(json: String, allowCleartext: Boolean = false): PackCatalog {
            val root = JSONObject(json)
            val schema = root.getInt("schema")
            require(schema in 1..SCHEMA) { "Catalog schema $schema is newer than this app reads" }
            val list = root.getJSONArray("packs")
            val packs = (0 until list.length()).mapNotNull { index ->
                runCatching { parsePack(list.getJSONObject(index), allowCleartext) }.getOrNull()
            }
            return PackCatalog(packs.distinctBy { it.id })
        }

        private fun parsePack(item: JSONObject, allowCleartext: Boolean): PackInfo? {
            val kind = PackKind.fromId(item.getString("kind")) ?: return null
            val id = item.getString("id").takeIf { Id.matches(it) } ?: return null
            val url = item.getString("url")
            val secure = url.startsWith("https://") || (allowCleartext && url.startsWith("http://"))
            if (!secure) return null
            val sha256 = item.getString("sha256").lowercase().takeIf { Sha256.matches(it) } ?: return null
            val compressed = item.getLong("compressedBytes").takeIf { it > 0 } ?: return null
            val installed = item.getLong("installedBytes").takeIf { it > 0 } ?: return null
            return PackInfo(
                id = id,
                kind = kind,
                version = item.getInt("version"),
                schema = item.getInt("schema"),
                title = item.getString("title"),
                work = item.optString("work"),
                author = item.optString("author"),
                url = url,
                sha256 = sha256,
                compressedBytes = compressed,
                installedBytes = installed,
                minAppVersionCode = item.optInt("minAppVersionCode", 0),
            )
        }
    }
}
