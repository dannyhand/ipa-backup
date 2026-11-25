package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.afc.AfcException
import us.dannyhand.ipabackup.afc.measureTree
import us.dannyhand.ipabackup.service.ByteConnection
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Minimal fake AFC server: parses each request packet and queues the reply. */
class FakeAfcDevice(private val files: Map<String, ByteArray>) : ByteConnection {
    private val reply = java.util.ArrayDeque<Byte>()
    private val openFiles = HashMap<Long, Pair<ByteArray, Int>>()
    private var nextHandle = 3L

    private fun packet(op: Long, params: ByteArray, payload: ByteArray = ByteArray(0)): ByteArray {
        val thisLen = 40 + params.size
        return ByteBuffer.allocate(thisLen + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("CFA6LPAA".toByteArray()).putLong((thisLen + payload.size).toLong()).putLong(thisLen.toLong())
            .putLong(0).putLong(op).put(params).put(payload).array()
    }
    private fun le(vararg v: Long) = ByteBuffer.allocate(8 * v.size).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putLong(it) } }.array()
    private fun status(code: Long) = packet(AfcClient.OP_STATUS, le(code))

    val openHandles get() = openFiles.size

    private fun isDir(p: String) = files.keys.any { it.startsWith(p.trimEnd('/') + "/") }

    override fun write(data: ByteArray) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val thisLen = bb.getLong(16).toInt()
        val op = bb.getLong(32)
        val params = data.copyOfRange(40, thisLen)
        val p = ByteBuffer.wrap(params).order(ByteOrder.LITTLE_ENDIAN)
        fun path(off: Int) = String(params, off, params.size - off - 1)
        val out = when (op) {
            AfcClient.OP_GET_FILE_INFO -> files[path(0)]?.let {
                packet(AfcClient.OP_DATA, ByteArray(0), "st_size\u0000${it.size}\u0000st_mtime\u00001356998400000000000\u0000st_ifmt\u0000S_IFREG\u0000".toByteArray())
            } ?: if (isDir(path(0))) {
                packet(AfcClient.OP_DATA, ByteArray(0), "st_size\u0000102\u0000st_ifmt\u0000S_IFDIR\u0000".toByteArray())
            } else status(8)
            AfcClient.OP_READ_DIR -> if (isDir(path(0))) {
                val prefix = path(0).trimEnd('/') + "/"
                val names = files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
                packet(AfcClient.OP_DATA, ByteArray(0), (listOf(".", "..") + names).joinToString("") { it + "\u0000" }.toByteArray())
            } else status(8)
            AfcClient.OP_FILE_OPEN -> files[path(8)]?.let {
                val h = nextHandle++; openFiles[h] = it to 0; packet(AfcClient.OP_FILE_OPEN_RES, le(h))
            } ?: status(8)
            AfcClient.OP_FILE_READ -> {
                val h = p.getLong(0); val len = p.getLong(8).toInt()
                val (bytes, pos) = openFiles.getValue(h)
                val end = minOf(bytes.size, pos + len)
                openFiles[h] = bytes to end
                // Real devices answer DATA with the bytes as payload (this_length = 40).
                packet(AfcClient.OP_DATA, ByteArray(0), bytes.copyOfRange(pos, end))
            }
            AfcClient.OP_FILE_CLOSE -> { openFiles.remove(p.getLong(0)); status(0) }
            else -> status(1)
        }
        out.forEach { reply.add(it) }
    }

    override fun readFully(n: Int, timeoutMs: Long): ByteArray {
        check(reply.size >= n) { "fake device has ${reply.size} bytes, client wants $n" }
        return ByteArray(n) { reply.removeFirst() }
    }

    override fun close() {}
}

class AfcClientTest {
    @Test
    fun copiesMultiChunkFileExactly() {
        val content = Random(42).nextBytes(AfcClient.READ_CHUNK * 3 + 12345)
        val afc = AfcClient(FakeAfcDevice(mapOf("ApplicationArchives/com.test.app.zip" to content)))
        val out = ByteArrayOutputStream()
        var lastProgress = 0L to 0L
        val n = afc.copyFile("ApplicationArchives/com.test.app.zip", out) { c, t -> lastProgress = c to t }
        assertEquals(content.size.toLong(), n)
        assertArrayEquals(content, out.toByteArray())
        assertEquals(content.size.toLong() to content.size.toLong(), lastProgress)
    }

    @Test
    fun cancelCheckAbortsMidCopyAndClosesHandle() {
        val content = Random(7).nextBytes(AfcClient.READ_CHUNK * 5)
        val device = FakeAfcDevice(mapOf("big.zip" to content))
        val afc = AfcClient(device)
        var chunks = 0
        afc.cancelCheck = { if (chunks >= 2) throw IllegalStateException("stop") }
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            afc.copyFile("big.zip", out) { _, _ -> chunks++ }
        }
        assertEquals(AfcClient.READ_CHUNK * 2, out.size())
        assertEquals(0, device.openHandles)
        // Connection is still in sync: a fresh copy works after cancelling.
        afc.cancelCheck = {}
        assertEquals(content.size.toLong(), afc.copyFile("big.zip", ByteArrayOutputStream()))
    }

    @Test
    fun emptyFile() {
        val afc = AfcClient(FakeAfcDevice(mapOf("e.zip" to ByteArray(0))))
        assertEquals(0L, afc.copyFile("e.zip", ByteArrayOutputStream()))
    }

    @Test
    fun missingFileThrowsNotFound() {
        val afc = AfcClient(FakeAfcDevice(emptyMap()))
        val e = assertThrows(AfcException::class.java) { afc.copyFile("nope.zip", ByteArrayOutputStream()) }
        assertEquals(8L, e.code)
    }
}

class AfcZipperTest {
    private val tree = mapOf(
        "/c/Foo.app/Foo" to "binary".toByteArray(),
        "/c/Foo.app/Info.plist" to "plist".toByteArray(),
        "/c/Foo.app/art/a.png" to Random(1).nextBytes(AfcClient.READ_CHUNK + 7),
        "/c/iTunesMetadata.plist" to "meta".toByteArray(),
        "/c/Documents/save.dat" to "progress".toByteArray(),
        "/c/Library/Preferences/com.foo.plist" to "prefs".toByteArray(),
        "/c/tmp/junk" to "junk".toByteArray(),
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() }
        }
        return m
    }

    @Test
    fun ipaFromAppBundle() {
        val afc = AfcClient(FakeAfcDevice(tree))
        val out = ByteArrayOutputStream()
        us.dannyhand.ipabackup.afc.AfcZipper.open(out).use { zos ->
            val z = us.dannyhand.ipabackup.afc.AfcZipper(afc, zos)
            z.addTree("/c/Foo.app", "Payload/Foo.app/")
            assertEquals(true, z.addFileIfExists("/c/iTunesMetadata.plist", "iTunesMetadata.plist"))
            assertEquals(false, z.addFileIfExists("/c/iTunesArtwork", "iTunesArtwork"))
            assertEquals(4, z.files)
        }
        val entries = unzip(out.toByteArray())
        assertEquals(
            setOf("Payload/Foo.app/", "Payload/Foo.app/Foo", "Payload/Foo.app/Info.plist", "Payload/Foo.app/art/",
                "Payload/Foo.app/art/a.png", "iTunesMetadata.plist"),
            entries.keys,
        )
        assertArrayEquals(tree["/c/Foo.app/art/a.png"], entries["Payload/Foo.app/art/a.png"])
    }

    @Test
    fun dataZipOnlyDocumentsAndLibrary() {
        val afc = AfcClient(FakeAfcDevice(tree))
        val out = ByteArrayOutputStream()
        us.dannyhand.ipabackup.afc.AfcZipper.open(out).use { zos ->
            us.dannyhand.ipabackup.afc.AfcZipper(afc, zos).addTree("/c", "") { it == "Documents" || it == "Library" }
        }
        val entries = unzip(out.toByteArray())
        assertEquals(setOf("Documents/", "Documents/save.dat", "Library/", "Library/Preferences/",
            "Library/Preferences/com.foo.plist"), entries.keys)
        assertArrayEquals("progress".toByteArray(), entries["Documents/save.dat"])
    }
}

class MeasureTreeTest {
    @Test
    fun totalsFilesAndEstimatesNearRealZipSize() {
        // Mix of incompressible (random .png) and very compressible (repetitive .plist) data.
        val tree = mapOf(
            "/d/a.png" to Random(3).nextBytes(200_000),
            "/d/sub/b.plist" to "<key>level</key><integer>7</integer>\n".repeat(3000).toByteArray(),
            "/d/sub/deeper/c.bin" to Random(4).nextBytes(50_000),
        )
        val afc = AfcClient(FakeAfcDevice(tree))
        var progressCalls = 0
        val size = afc.measureTree("/d", onProgress = { progressCalls++ })
        assertEquals(3, size.files)
        assertEquals(tree.values.sumOf { it.size.toLong() }, size.bytes)
        assertEquals(3, progressCalls)

        // Compare with a real zip of the same folder.
        val out = ByteArrayOutputStream()
        us.dannyhand.ipabackup.afc.AfcZipper.open(out).use { zos ->
            us.dannyhand.ipabackup.afc.AfcZipper(AfcClient(FakeAfcDevice(tree)), zos).addTree("/d", "d/")
        }
        val actual = out.size().toLong()
        println("estimate=${size.estimatedZipBytes} actual=$actual uncompressed=${size.bytes}")
        // Estimate should be in the right ballpark: never below the real size by much, and well under raw size + 25%.
        assert(size.estimatedZipBytes >= actual * 0.8) { "estimate ${size.estimatedZipBytes} too low vs $actual" }
        assert(size.estimatedZipBytes <= size.bytes * 1.25) { "estimate too high" }
    }

    @Test
    fun cancelStopsWalk() {
        val tree = (1..20).associate { "/d/f$it.txt" to "x".toByteArray() }
        val afc = AfcClient(FakeAfcDevice(tree))
        var n = 0
        assertThrows(us.dannyhand.ipabackup.afc.CancelledException::class.java) {
            afc.measureTree("/d", onProgress = { n++ }, isCancelled = { n >= 5 })
        }
    }
}
