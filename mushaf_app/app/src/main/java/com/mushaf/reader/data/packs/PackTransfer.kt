package com.mushaf.reader.data.packs

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException

/** A pack that arrived but is not the file the catalog promised. Retrying the same file won't help. */
class PackIntegrityException(message: String) : Exception(message)

/**
 * The plain-Java steps of fetching a pack: a resumable HTTPS download, its checksum, and unpacking.
 * No Android types, so they are tested on the JVM.
 */
object PackTransfer {

    private const val USER_AGENT = "Natlu (Android)"
    private const val MAX_REDIRECTS = 5
    private const val BUFFER = 64 * 1024

    /** The first byte a `206 Partial Content` reply starts at, from `Content-Range: bytes a-b/n`. */
    fun contentRangeStart(header: String?): Long? =
        header?.let { Regex("""bytes\s+(\d+)-\d+/(?:\d+|\*)""").find(it.trim()) }
            ?.groupValues?.get(1)?.toLongOrNull()

    /**
     * Continue [part] from where it stopped, or start it over when the server sends the whole file.
     * [shouldStop] is polled between chunks; a stop throws [CancellationException] and keeps what
     * arrived, for the next attempt to resume from.
     */
    fun download(
        url: String,
        part: File,
        expectedBytes: Long,
        allowCleartext: Boolean,
        shouldStop: () -> Boolean,
        onProgress: (Long) -> Unit,
    ) {
        var from = part.length()
        if (from > expectedBytes) {
            part.delete()
            from = 0
        }
        if (from == expectedBytes) return
        val connection = open(url, from, allowCleartext)
        try {
            val code = connection.responseCode
            val append = when {
                from > 0 && code == HttpURLConnection.HTTP_PARTIAL &&
                    contentRangeStart(connection.getHeaderField("Content-Range")) == from -> true
                code == HttpURLConnection.HTTP_OK -> false
                code == 416 -> {
                    part.delete()
                    throw IOException("The server refused to resume; starting over next time")
                }
                else -> throw IOException("HTTP $code")
            }
            if (!append) from = 0
            FileOutputStream(part, append).use { out ->
                connection.inputStream.use { input ->
                    copy(input, out, from, expectedBytes, shouldStop, onProgress)
                }
            }
            if (part.length() != expectedBytes) {
                throw IOException("Download stopped at ${part.length()} of $expectedBytes bytes")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun copy(
        input: InputStream,
        out: FileOutputStream,
        start: Long,
        expectedBytes: Long,
        shouldStop: () -> Boolean,
        onProgress: (Long) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER)
        var done = start
        while (true) {
            if (shouldStop()) throw CancellationException("Download stopped")
            val read = input.read(buffer)
            if (read < 0) break
            done += read
            if (done > expectedBytes) throw PackIntegrityException("The file is larger than the catalog says")
            out.write(buffer, 0, read)
            onProgress(done)
        }
    }

    /**
     * Opens [url], following redirects by hand (GitHub sends release downloads to its file host) so
     * that the Range header goes along and no redirect can leave HTTPS.
     */
    fun open(url: String, from: Long, allowCleartext: Boolean): HttpURLConnection {
        var current = URL(url)
        if (current.protocol != "https" && !(allowCleartext && current.protocol == "http")) {
            throw IOException("Refusing a download that is not HTTPS")
        }
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            // Without this Android may unzip on the fly, and byte offsets would no longer match.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
            val code = connection.responseCode
            if (code in 301..308 && code != 304) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                location ?: throw IOException("Redirect without a location")
                val next = URL(current, location)
                if (next.protocol != "https" && !(allowCleartext && next.protocol == "http")) {
                    throw IOException("Refusing a redirect away from HTTPS")
                }
                current = next
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects")
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Unpacks [source] into [target], refusing to write more than [limitBytes]. */
    fun gunzip(source: File, target: File, limitBytes: Long) {
        GZIPInputStream(BufferedInputStream(FileInputStream(source), BUFFER)).use { input ->
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(BUFFER)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > limitBytes) throw PackIntegrityException("The pack unpacks larger than it should")
                    out.write(buffer, 0, read)
                }
            }
        }
    }
}
