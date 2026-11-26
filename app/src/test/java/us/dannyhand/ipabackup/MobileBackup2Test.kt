package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.backup.DeviceLink
import us.dannyhand.ipabackup.backup.MobileBackup2
import us.dannyhand.ipabackup.plist.BinaryPlist
import us.dannyhand.ipabackup.plist.BinaryPlistWriter
import us.dannyhand.ipabackup.service.ByteConnection
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.util.Date

class BinaryPlistWriterTest {
    @Test fun roundTrips() {
        val big = (1..300).map { "item $it" }
        val value = mapOf(
            "s" to "plain", "u" to "Señor ✓", "i" to 7L, "i2" to 70000L, "neg" to -13L, "huge" to (1L shl 40),
            "r" to 2.5, "t" to true, "f" to false, "d" to byteArrayOf(1, 2, 3), "date" to Date(1_700_000_000_000L),
            "list" to listOf(1L, "two", listOf(3L)), "big" to big, "empty" to emptyMap<String, Any>(), "long string" to "x".repeat(40),
        )
        @Suppress("UNCHECKED_CAST")
        val back = BinaryPlist.parse(BinaryPlistWriter.write(value)) as Map<String, Any?>
        assertEquals("plain", back["s"]); assertEquals("Señor ✓", back["u"])
        assertEquals(7L, (back["i"] as Number).toLong()); assertEquals(70000L, (back["i2"] as Number).toLong())
        assertEquals(-13L, (back["neg"] as Number).toLong()); assertEquals(1L shl 40, (back["huge"] as Number).toLong())
        assertEquals(2.5, (back["r"] as Number).toDouble(), 0.0)
        assertEquals(true, back["t"]); assertEquals(false, back["f"])
        assertArrayEquals(byteArrayOf(1, 2, 3), back["d"] as ByteArray)
        assertEquals(Date(1_700_000_000_000L), back["date"])
        assertEquals(listOf(1L, "two", listOf(3L)), (back["list"] as List<*>).map { if (it is Number) it.toLong() else if (it is List<*>) it.map { x -> (x as Number).toLong() } else it })
        assertEquals(big, back["big"])
        assertEquals("x".repeat(40), back["long string"])
        assertTrue((back["empty"] as Map<*, *>).isEmpty())
    }
}

/** A pipe pair standing in for the USB connection: one end for the app, one for the simulated device. */
private class Pipe : ByteConnection {
    val toDevice = PipedOutputStream()
    val fromApp = PipedInputStream(toDevice, 1 shl 20)
    val toApp = PipedOutputStream()
    val fromDevice = PipedInputStream(toApp, 1 shl 20)
    private val appIn = DataInputStream(fromDevice)
    override fun write(data: ByteArray) { toDevice.write(data); toDevice.flush() }
    override fun readFully(n: Int, timeoutMs: Long): ByteArray = ByteArray(n).also { appIn.readFully(it) }
    override fun close() {}
}

/** The device's side of the conversation, scripted. */
private class FakeDevice(p: Pipe) {
    val out = DataOutputStream(p.toApp)
    val inp = DataInputStream(p.fromApp)
    fun send(vararg items: Any) { val b = BinaryPlistWriter.write(items.toList()); out.writeInt(b.size); out.write(b); out.flush() }
    fun recv(): List<*> { val n = inp.readInt(); return BinaryPlist.parse(ByteArray(n).also { inp.readFully(it) }) as List<*> }
    fun status(): List<*> = recv().also { check(it[0] == "DLMessageStatusResponse") { "expected a status, got $it" } }
    fun block(code: Int, data: ByteArray = ByteArray(0)) { out.writeInt(data.size + 1); out.write(code); out.write(data) }
    fun name(s: String) { val b = s.toByteArray(); out.writeInt(b.size); out.write(b) }
}

class MobileBackup2Test {
    private val udid = "0123456789abcdef0123456789abcdef01234567"

    @Test fun fullConversation() {
        val root = Files.createTempDirectory("mb2").toFile()
        val pipe = Pipe()
        val seen = ArrayList<String>()
        var deviceError: Throwable? = null
        val big = ByteArray(150_000) { (it % 251).toByte() }
        val device = Thread {
            try {
                val d = FakeDevice(pipe)
                d.send("DLMessageVersionExchange", 300L, 0L)
                val ok = d.recv(); check(ok[0] == "DLMessageVersionExchange" && ok[1] == "DLVersionsOk")
                d.send("DLMessageDeviceReady")
                val hello = d.recv()[1] as Map<*, *>
                check(hello["MessageName"] == "Hello")
                d.send("DLMessageProcessMessage", mapOf("MessageName" to "Response", "ErrorCode" to 0L, "ProtocolVersion" to 2.1))
                val req = d.recv()[1] as Map<*, *>
                check(req["MessageName"] == "Backup" && req["TargetIdentifier"] == udid) { "bad request $req" }
                seen += "full=" + ((req["Options"] as? Map<*, *>)?.get("ForceFullBackup") == true)

                d.send("DLMessageCreateDirectory", "$udid/Snapshot", 1.0)
                seen += "mkdir " + d.status()[1]

                // Asks for a previous Status.plist: there isn't one.
                d.send("DLMessageDownloadFiles", listOf("$udid/Status.plist"), emptyMap<String, Any>(), 2.0)
                val n = d.inp.readInt(); val path = String(ByteArray(n).also { d.inp.readFully(it) })
                val len = d.inp.readInt(); val code = d.inp.read(); ByteArray(len - 1).also { d.inp.readFully(it) }
                check(path == "$udid/Status.plist" && code == 0x06) { "expected not-found for $path, code $code" }
                check(d.inp.readInt() == 0)
                val st = d.status()
                seen += "download ${st[1]} ${((st[3] as Map<*, *>)["$udid/Status.plist"] as Map<*, *>)["DLFileErrorCode"]}"

                // Sends three files: one in 3 blocks, one empty, one it fails to read.
                d.send("DLMessageUploadFiles", emptyMap<String, Any>(), 10.0)
                d.name("/var/mobile/whatever"); d.name("$udid/Snapshot/aaaa")
                d.block(0x0C, big.copyOfRange(0, 65536)); d.block(0x0C, big.copyOfRange(65536, 131072)); d.block(0x0C, big.copyOfRange(131072, big.size))
                d.block(0x00)
                d.name("x"); d.name("$udid/Snapshot/empty"); d.block(0x00)
                d.name("y"); d.name("$udid/Snapshot/broken"); d.block(0x0C, byteArrayOf(9, 9)); d.block(0x0B, "Permission denied".toByteArray())
                d.out.writeInt(0); d.out.flush()
                seen += "upload " + d.status()[1]

                d.send("DLMessageGetFreeDiskSpace", 50.0)
                seen += "free " + ((d.status()[3] as Number).toLong() > 0)

                d.send("DLContentsOfDirectory", "$udid/Snapshot", 60.0)
                seen += "ls " + (d.status()[3] as Map<*, *>).keys.map { it.toString() }.sorted()

                d.send("DLMessageMoveItems", mapOf("$udid/Snapshot/aaaa" to "$udid/aaaa", "$udid/Snapshot/empty" to "$udid/empty"), 90.0)
                d.status()
                d.send("DLMessageRemoveItems", listOf("$udid/Snapshot"), 95.0)
                d.status()
                d.send("DLMessageProcessMessage", mapOf("ErrorCode" to 0L, "MessageName" to "Response"))
            } catch (t: Throwable) { deviceError = t }
        }
        device.start()
        val link = DeviceLink(pipe)
        val mb = MobileBackup2(link, root)
        val events = ArrayList<String>()
        mb.onEvent = { events += it }
        assertEquals(2.1, mb.connect(), 0.0)
        mb.backup(udid, full = true)
        device.join(5000)
        deviceError?.let { throw it }

        assertEquals(listOf("full=true", "mkdir 0", "download -13 -6", "upload 0", "free true", "ls [aaaa, empty]"), seen)
        assertArrayEquals(big, File(root, "$udid/aaaa").readBytes())
        assertEquals(0L, File(root, "$udid/empty").length())
        assertFalse(File(root, "$udid/Snapshot").exists())
        assertFalse(File(root, "$udid/Snapshot/broken").exists())
        assertEquals(2, mb.stats.filesReceived)
        assertEquals(95.0, mb.stats.progress, 0.0)
        assertTrue(events.any { it.contains("Permission denied") })
        root.deleteRecursively()
    }

    @Test fun deviceErrorAndPathEscape() {
        val root = Files.createTempDirectory("mb2").toFile()
        val pipe = Pipe()
        Thread {
            runCatching {
                val d = FakeDevice(pipe)
                d.send("DLMessageVersionExchange", 300L, 0L); d.recv(); d.send("DLMessageDeviceReady")
                d.recv(); d.send("DLMessageProcessMessage", mapOf("MessageName" to "Response", "ErrorCode" to 0L, "ProtocolVersion" to 2.0))
                d.recv()
                d.send("DLMessageCreateDirectory", "../outside")
            }
        }.start()
        val mb = MobileBackup2(DeviceLink(pipe), root)
        mb.connect()
        val e = runCatching { mb.backup(udid, false) }.exceptionOrNull()
        assertTrue(e?.message, e?.message?.contains("outside the backup") == true)
        assertFalse(File(root.parentFile, "outside").exists())

        val pipe2 = Pipe()
        Thread {
            runCatching {
                val d = FakeDevice(pipe2)
                d.send("DLMessageVersionExchange", 300L, 0L); d.recv(); d.send("DLMessageDeviceReady")
                d.recv(); d.send("DLMessageProcessMessage", mapOf("MessageName" to "Response", "ErrorCode" to 0L))
                d.recv()
                d.send("DLMessageProcessMessage", mapOf("ErrorCode" to 105L, "ErrorDescription" to "Insufficient free disk space"))
            }
        }.start()
        val mb2 = MobileBackup2(DeviceLink(pipe2), root)
        mb2.connect()
        val e2 = runCatching { mb2.backup(udid, false) }.exceptionOrNull()
        assertEquals("Insufficient free disk space (error 105)", e2?.message)
        root.deleteRecursively()
    }
}
