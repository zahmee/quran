package com.mushaf.reader.data.packs

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

class PackTransferTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val body = Random(7).nextBytes(300_000)
    private lateinit var server: HttpServer
    private var honourRange = true
    private val ranges = mutableListOf<String?>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.gz") { exchange -> serve(exchange) }
        server.createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", "/file.gz")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun serve(exchange: HttpExchange) {
        val range = exchange.requestHeaders.getFirst("Range")
        ranges += range
        val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
        if (honourRange && from != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
            exchange.sendResponseHeaders(206, (body.size - from).toLong())
            exchange.responseBody.use { it.write(body, from, body.size - from) }
        } else {
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun fetch(part: File, path: String = "/file.gz") = PackTransfer.download(
        url = url(path),
        part = part,
        expectedBytes = body.size.toLong(),
        allowCleartext = true,
        shouldStop = { false },
        onProgress = {},
    )

    @Test
    fun contentRangeGivesTheFirstByte() {
        assertEquals(100L, PackTransfer.contentRangeStart("bytes 100-199/200"))
        assertEquals(0L, PackTransfer.contentRangeStart(" bytes 0-9/*"))
        assertNull(PackTransfer.contentRangeStart("items 1-2/3"))
        assertNull(PackTransfer.contentRangeStart(null))
    }

    @Test
    fun aFreshDownloadGetsTheWholeFile() {
        val part = folder.newFile("a.part").apply { delete() }
        fetch(part)
        assertArrayEquals(body, part.readBytes())
        assertEquals(listOf<String?>(null), ranges)
    }

    @Test
    fun aStoppedDownloadResumesWhereItStopped() {
        val part = folder.newFile("b.part")
        part.writeBytes(body.copyOfRange(0, 120_000))
        fetch(part)
        assertArrayEquals(body, part.readBytes())
        assertEquals(listOf<String?>("bytes=120000-"), ranges)
    }

    @Test
    fun aServerThatIgnoresTheRangeStartsTheFileOver() {
        honourRange = false
        val part = folder.newFile("c.part")
        part.writeBytes(body.copyOfRange(0, 50_000))
        fetch(part)
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun aRedirectIsFollowedWithTheRangeKept() {
        val part = folder.newFile("d.part")
        part.writeBytes(body.copyOfRange(0, 1_000))
        fetch(part, "/moved")
        assertArrayEquals(body, part.readBytes())
        assertEquals(listOf<String?>("bytes=1000-"), ranges)
    }

    @Test(expected = IOException::class)
    fun cleartextIsRefusedUnlessAllowed() {
        PackTransfer.download(url("/file.gz"), folder.newFile("e.part"), body.size.toLong(), false, { false }) {}
    }

    @Test(expected = PackIntegrityException::class)
    fun aFileLargerThanPromisedIsRefused() {
        PackTransfer.download(url("/file.gz"), folder.newFile("f.part").apply { delete() }, 1_000L, true, { false }) {}
    }

    @Test
    fun checksumAndUnpacking() {
        val plain = "سورة الفاتحة".repeat(1_000).toByteArray()
        val packed = folder.newFile("g.gz")
        GZIPOutputStream(packed.outputStream()).use { it.write(plain) }
        val out = folder.newFile("g.db")
        PackTransfer.gunzip(packed, out, limitBytes = plain.size.toLong())
        assertArrayEquals(plain, out.readBytes())
        val empty = folder.newFile("empty")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", PackTransfer.sha256(empty))
    }

    @Test
    fun unpackingStopsAtTheLimit() {
        val packed = folder.newFile("h.gz")
        GZIPOutputStream(packed.outputStream()).use { it.write(ByteArray(100_000)) }
        val failed = runCatching { PackTransfer.gunzip(packed, folder.newFile("h.db"), limitBytes = 10_000) }
        assertTrue(failed.exceptionOrNull() is PackIntegrityException)
    }
}
