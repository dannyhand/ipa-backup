package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
class FakeAfcDevice(initial: Map<String, ByteArray>) : ByteConnection {
    /** Files on the fake device; tests can inspect what was written. */
    val files = LinkedHashMap(initial)
    /** Folders exist on their own (like on the device), starting with every parent of the initial files. */
    val dirs = HashSet<String>().apply {
        for (f in initial.keys) { var d = f.substringBeforeLast('/', ""); while (d.isNotEmpty()) { add(d); d = d.substringBeforeLast('/', "") } }
    }
    private val reply = java.util.ArrayDeque<Byte>()
    private val openFiles = HashMap<Long, Pair<ByteArray, Int>>()
    private val writeHandles = HashMap<Long, String>()
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

    private fun isDir(p: String) = p.trimEnd('/') in dirs || files.keys.any { it.startsWith(p.trimEnd('/') + "/") }

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
            AfcClient.OP_FILE_OPEN -> if (p.getLong(0) == AfcClient.MODE_WRONLY) {
                val target = path(8)
                if (target.substringBeforeLast('/', "") .let { it.isNotEmpty() && !isDir(it) }) status(8) else {
                    files[target] = ByteArray(0)
                    val h = nextHandle++; writeHandles[h] = target; packet(AfcClient.OP_FILE_OPEN_RES, le(h))
                }
            } else files[path(8)]?.let {
                val h = nextHandle++; openFiles[h] = it to 0; packet(AfcClient.OP_FILE_OPEN_RES, le(h))
            } ?: status(8)
            AfcClient.OP_FILE_WRITE -> {
                val target = writeHandles.getValue(p.getLong(0))
                files[target] = files.getValue(target) + data.copyOfRange(thisLen, data.size)
                status(0)
            }
            AfcClient.OP_MAKE_DIR -> {
                var d = ""
                for (part in path(0).trim('/').split('/')) { d += "/$part"; dirs += d } // mkdir -p
                status(0)
            }
            AfcClient.OP_RENAME_PATH -> {
                val parts = String(params, 0, params.size - 1).split('\u0000')
                val from = parts[0].trimEnd('/'); val to = parts[1].trimEnd('/')
                val bytes = files.remove(from)
                if (bytes == null) status(8) else { files[to] = bytes; status(0) }
            }
            AfcClient.OP_REMOVE_PATH -> {
                val target = path(0).trimEnd('/')
                when {
                    files.remove(target) != null -> status(0)
                    target in dirs && (files.keys + dirs).any { it.startsWith("$target/") } -> status(1) // not empty
                    dirs.remove(target) -> status(0)
                    else -> status(8)
                }
            }
            AfcClient.OP_FILE_READ -> {
                val h = p.getLong(0); val len = p.getLong(8).toInt()
                val (bytes, pos) = openFiles.getValue(h)
                val end = minOf(bytes.size, pos + len)
                openFiles[h] = bytes to end
                // Real devices answer DATA with the bytes as payload (this_length = 40).
                packet(AfcClient.OP_DATA, ByteArray(0), bytes.copyOfRange(pos, end))
            }
            AfcClient.OP_FILE_CLOSE -> { openFiles.remove(p.getLong(0)); writeHandles.remove(p.getLong(0)); status(0) }
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

class AfcWriteTest {
    @Test
    fun uploadMultiChunkFileThenRemove() {
        val device = FakeAfcDevice(emptyMap())
        val afc = AfcClient(device)
        afc.makeDir("/PublicStaging")
        val content = Random(11).nextBytes(AfcClient.WRITE_CHUNK * 3 + 777)
        var last = 0L
        val n = afc.writeFile("/PublicStaging/game.ipa", content.inputStream(), content.size.toLong()) { w, _ -> last = w }
        assertEquals(content.size.toLong(), n)
        assertEquals(content.size.toLong(), last)
        assertArrayEquals(content, device.files["/PublicStaging/game.ipa"])
        afc.remove("/PublicStaging/game.ipa")
        assertTrue("/PublicStaging/game.ipa" !in device.files)
    }

    @Test
    fun writeIntoMissingFolderFailsAndExactChunkSizeWorks() {
        val device = FakeAfcDevice(emptyMap())
        val afc = AfcClient(device)
        assertThrows(AfcException::class.java) { afc.writeFile("/Nope/x.bin", ByteArray(10).inputStream()) }
        afc.makeDir("/A/B/C") // parents created too
        val exact = ByteArray(AfcClient.WRITE_CHUNK) { 5 }
        afc.writeFile("/A/B/C/x.bin", exact.inputStream())
        assertArrayEquals(exact, device.files["/A/B/C/x.bin"])
        afc.writeFile("/A/B/C/empty.bin", ByteArray(0).inputStream())
        assertEquals(0, device.files["/A/B/C/empty.bin"]!!.size)
    }
}

class AfcRemoveTreeTest {
    @Test
    fun removesNestedFolderAndLeavesNeighbours() {
        val device = FakeAfcDevice(mapOf(
            "/PublicStaging/game.ipa/Payload/Game.app/Game" to ByteArray(10),
            "/PublicStaging/game.ipa/iTunesMetadata.plist" to ByteArray(3),
            "/PublicStaging/other.ipa" to ByteArray(5),
            "/ApplicationArchives/com.x.zip" to ByteArray(7),
        ))
        val afc = AfcClient(device)
        assertEquals(2, afc.removeTree("/PublicStaging/game.ipa"))
        assertEquals(1, afc.removeTree("/PublicStaging/other.ipa"))
        assertEquals(setOf("/ApplicationArchives/com.x.zip"), device.files.keys)
        assertTrue("/PublicStaging/game.ipa" !in device.dirs) // folder itself removed
        assertTrue("/PublicStaging" in device.dirs)           // parent kept
    }
}

class AfcSafeRestoreTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { z ->
            for ((n, t) in entries) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
        }
        return bos.toByteArray()
    }

    @Test
    fun swapsFilesKeepingBackups() {
        val device = FakeAfcDevice(mapOf(
            "/iTunes_Control/iTunes/MediaLibrary.sqlitedb" to "current".toByteArray(),
            "/iTunes_Control/iTunes/MediaLibrary.sqlitedb-wal" to "current wal".toByteArray(),
            "/iTunes_Control/iTunes/MediaLibrary.sqlitedb.backup" to "older".toByteArray(),
        ))
        val r = us.dannyhand.ipabackup.afc.AfcSafeRestore.restore(
            AfcClient(device),
            zip("README.txt" to "x", "iTunes_Control/iTunes/MediaLibrary.sqlitedb" to "restored", "iTunes_Control/iTunes/Play Counts" to "pc").inputStream(),
            allowedTop = setOf("iTunes_Control"),
            retire = listOf("/iTunes_Control/iTunes/MediaLibrary.sqlitedb-wal"),
        ).let { it.files to it.replaced }
        assertEquals(2 to 1, r)
        val f = device.files.mapValues { String(it.value) }
        assertEquals("restored", f["/iTunes_Control/iTunes/MediaLibrary.sqlitedb"])
        assertEquals("current", f["/iTunes_Control/iTunes/MediaLibrary.sqlitedb.backup"])   // old .backup replaced
        assertEquals("current wal", f["/iTunes_Control/iTunes/MediaLibrary.sqlitedb-wal.backup"]) // stale WAL moved aside
        assertEquals("pc", f["/iTunes_Control/iTunes/Play Counts"])
        assertTrue(f.keys.none { it.endsWith(".ipabackup-new") || it.startsWith("/README") })
        assertTrue("/iTunes_Control/iTunes/MediaLibrary.sqlitedb-wal" !in f)
    }

    @Test
    fun interruptedCopyLeavesLibraryUntouched() {
        val device = FakeAfcDevice(mapOf("/iTunes_Control/iTunes/MediaLibrary.sqlitedb" to "current".toByteArray()))
        val noise = java.util.Random(1).let { r -> String(CharArray(20000) { ('a' + r.nextInt(26)) }) } // doesn't compress
        val good = zip("iTunes_Control/iTunes/MediaLibrary.sqlitedb" to "restored", "iTunes_Control/iTunes/Other" to noise)
        // The connection dies partway through the second file (like the cable coming out mid-copy).
        val dying = object : java.io.FilterInputStream(good.inputStream()) {
            var n = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (n > good.size / 2) throw java.io.IOException("cable out")
                return super.read(b, off, minOf(len, 512)).also { if (it > 0) n += it }
            }
        }
        val thrown = runCatching { us.dannyhand.ipabackup.afc.AfcSafeRestore.restore(AfcClient(device), dying, setOf("iTunes_Control")) }
        assertTrue(thrown.isFailure)
        assertEquals("current", String(device.files.getValue("/iTunes_Control/iTunes/MediaLibrary.sqlitedb")))
        assertTrue(device.files.keys.none { it.endsWith(".ipabackup-new") || it.endsWith(".backup") })
    }
}
