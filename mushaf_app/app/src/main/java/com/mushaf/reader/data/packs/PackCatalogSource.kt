package com.mushaf.reader.data.packs

import android.content.Context
import com.mushaf.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Where the list of packs comes from: the published catalog when the library is opened, else the
 * copy saved the last time, else the one shipped in the app. Nothing fetches it in the background.
 */
class PackCatalogSource(private val context: Context) {

    private val cacheFile: File get() = File(PackStore.dir(context), "catalog.json")

    /** The saved or shipped catalog, without touching the network. */
    suspend fun offline(): PackCatalog? = withContext(Dispatchers.IO) {
        cacheFile.takeIf { it.isFile }?.let { parseOrNull(it.readText()) }
            ?: runCatching { context.assets.open(BUNDLED).bufferedReader().use { it.readText() } }
                .getOrNull()?.let(::parseOrNull)
    }

    /** The published catalog; it replaces the saved copy only once it has been read successfully. */
    suspend fun fetch(): PackCatalog = withContext(Dispatchers.IO) {
        val connection = PackTransfer.open(BuildConfig.PACK_CATALOG_URL, 0L, allowCleartext = BuildConfig.DEBUG)
        val text = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_CATALOG_BYTES) throw IOException("Catalog too large")
                }
                out.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
        val catalog = PackCatalog.parse(text, allowCleartext = BuildConfig.DEBUG)
        val temporary = File(cacheFile.path + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(cacheFile)) {
            cacheFile.delete()
            temporary.renameTo(cacheFile)
        }
        catalog
    }

    private fun parseOrNull(text: String): PackCatalog? =
        runCatching { PackCatalog.parse(text, allowCleartext = BuildConfig.DEBUG) }.getOrNull()

    private companion object {
        const val BUNDLED = "packs/catalog.json"
        const val MAX_CATALOG_BYTES = 256 * 1024
    }
}
