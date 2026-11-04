package com.mushaf.reader.data.recitation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The reciter list and ayah timing tables from MP3Quran's public v3 API, kept on the device.
 *
 * Nothing here runs until the reader starts a recitation or opens the reciter list. A timing table
 * is fetched once per reciter and surah and then read from disk for good (the recordings do not
 * change); the reciter list is re-fetched after a week, and the copy on disk is used whenever the
 * network fails.
 */
class RecitationRepository(context: Context) {

    private val root = File(context.filesDir, "recitation")

    suspend fun reciters(forceRefresh: Boolean = false): List<Reciter> = withContext(Dispatchers.IO) {
        val file = File(root, "reads.json")
        val cached = file.takeIf { it.isFile }
            ?.let { runCatching { RecitationJson.parseReciters(it.readText()) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
        val fresh = cached != null && System.currentTimeMillis() - file.lastModified() < LIST_MAX_AGE_MS
        if (cached != null && fresh && !forceRefresh) return@withContext cached
        try {
            val body = fetch("$API/ayat_timing/reads")
            val parsed = RecitationJson.parseReciters(body)
            if (parsed.isEmpty()) throw IOException("Empty reciter list")
            writeAtomically(file, body)
            parsed
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    suspend fun timing(reciterId: Int, surah: Int): SurahTiming = withContext(Dispatchers.IO) {
        val file = File(root, "timing/$reciterId/${surah.toString().padStart(3, '0')}.json")
        file.takeIf { it.isFile }
            ?.let { runCatching { RecitationJson.parseTiming(surah, it.readText()) }.getOrNull() }
            ?.let { return@withContext it }
        val body = fetch("$API/ayat_timing?surah=$surah&read=$reciterId")
        val parsed = RecitationJson.parseTiming(surah, body) // throws before anything is cached
        writeAtomically(file, body)
        parsed
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    companion object {
        private const val API = "https://www.mp3quran.net/api/v3"
        private const val LIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val USER_AGENT = "Natlu (Android)"
    }
}
