package us.dannyhand.ipabackup.backup

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Date

/**
 * com.apple.mobilebackup2: the iTunes backup service (iOS 4 and later), following libimobiledevice's
 * mobilebackup2.c and idevicebackup2.c.
 *
 * After a DeviceLink version exchange and a "Hello" naming the protocol versions we speak, we send a
 * "Backup" request. From then on the device drives: it sends requests and we act on a folder on this
 * phone (the backup root, which holds one folder per device, named by its UDID), replying with a
 * status message after each one:
 *  - DLMessageUploadFiles: the device sends us files (its data, Manifest.mbdb, Status.plist…)
 *  - DLMessageDownloadFiles: it asks for files back (to compare with a previous backup)
 *  - DLMessageCreateDirectory / MoveFiles / MoveItems / RemoveFiles / RemoveItems / CopyItem
 *  - DLContentsOfDirectory and DLMessageGetFreeDiskSpace
 *  - DLMessageProcessMessage: the final result (ErrorCode 0 = success), or DLMessageDisconnect
 * Paths in requests are relative to the backup root and start with the UDID folder. Files are named
 * by the SHA-1 of their domain and path; Manifest.mbdb (iOS 5–9) maps them back.
 *
 * File transfers use raw blocks, not plists: the path (4-byte big-endian length + bytes), then
 * blocks of [4-byte length][1-byte code][data] where code 0x0C is file data, 0x00 ends the file
 * successfully, and 0x06/0x0B carry an error message. A zero length ends the batch.
 */
class MobileBackup2(private val link: DeviceLink, private val root: File) {
    companion object {
        const val SERVICE = "com.apple.mobilebackup2"
        private const val CODE_SUCCESS = 0x00
        private const val CODE_ERROR_LOCAL = 0x06
        private const val CODE_ERROR_REMOTE = 0x0B
        private const val CODE_FILE_DATA = 0x0C
        private const val BLOCK = 1 shl 16
        /** Device errors are negative errno-like codes; -6 = "no such file", -13 = several results (see the dict). */
        private const val ERR_NOT_FOUND = -6L
        private const val ERR_MULTI = -13L
        /** The device can go quiet for minutes while it prepares (e.g. snapshotting a big Camera Roll). */
        private const val WAIT_MS = 30 * 60_000L
    }

    class Stats {
        @Volatile var filesReceived = 0
        @Volatile var bytesReceived = 0L
        @Volatile var progress = -1.0
    }

    val stats = Stats()
    /** Called often from the transfer loop; throw to stop (the device is told the backup was cancelled by disconnecting). */
    var cancelCheck: () -> Unit = {}
    var onEvent: (String) -> Unit = {}

    /** Version exchange and Hello. Returns the protocol version the device chose. */
    fun connect(): Double {
        link.versionExchange()
        link.sendProcessMessage(mapOf("MessageName" to "Hello", "SupportedProtocolVersions" to listOf(2.0, 2.1)))
        val r = link.receiveProcessMessage(60_000)
        if (r["MessageName"] != "Response") throw IOException("Unexpected reply to Hello: $r")
        val err = (r["ErrorCode"] as? Number)?.toLong() ?: 0
        if (err != 0L) throw IOException("The device refused the backup protocol (error $err)")
        return (r["ProtocolVersion"] as? Number)?.toDouble() ?: 2.0
    }

    /**
     * Runs a backup into root/<udid>. Returns normally when the device reports success, throws with its
     * error description otherwise.
     */
    fun backup(udid: String, full: Boolean) {
        File(root, udid).mkdirs()
        val req = linkedMapOf<String, Any?>("MessageName" to "Backup", "TargetIdentifier" to udid)
        if (full) req["Options"] = mapOf("ForceFullBackup" to true)
        link.sendProcessMessage(req)
        serve()
    }

    /** Handles device requests until it reports the result. */
    private fun serve() {
        while (true) {
            cancelCheck()
            val msg = link.receive(WAIT_MS)
            val name = msg.firstOrNull() as? String ?: throw IOException("Empty message from the device")
            msg.drop(2).firstOrNull { it is Double }?.let { stats.progress = it as Double }
            when (name) {
                "DLMessageDownloadFiles" -> sendFiles(msg.getOrNull(1) as? List<*> ?: emptyList<Any>())
                "DLMessageUploadFiles" -> receiveFiles()
                "DLMessageGetFreeDiskSpace" -> link.sendStatus(0, null, root.usableSpace)
                "DLContentsOfDirectory" -> link.sendStatus(0, null, contentsOf(msg.getOrNull(1) as? String ?: ""))
                "DLMessageCreateDirectory" -> {
                    val d = resolve(msg.getOrNull(1) as? String)
                    val ok = d.isDirectory || d.mkdirs()
                    link.sendStatus(if (ok) 0 else -1, if (ok) null else "Can't create ${d.name}")
                }
                "DLMessageMoveFiles", "DLMessageMoveItems" -> {
                    for ((src, dst) in msg.getOrNull(1) as? Map<*, *> ?: emptyMap<Any, Any>()) {
                        val from = resolve(src as? String)
                        val to = resolve(dst as? String)
                        if (to.exists()) to.deleteRecursively()
                        to.parentFile?.mkdirs()
                        if (from.exists() && !from.renameTo(to)) { from.copyRecursively(to, true); from.deleteRecursively() }
                    }
                    link.sendStatus(0, null)
                }
                "DLMessageRemoveFiles", "DLMessageRemoveItems" -> {
                    for (p in msg.getOrNull(1) as? List<*> ?: emptyList<Any>()) resolve(p as? String).deleteRecursively()
                    link.sendStatus(0, null)
                }
                "DLMessageCopyItem" -> {
                    val from = resolve(msg.getOrNull(1) as? String)
                    val to = resolve(msg.getOrNull(2) as? String)
                    if (to.exists()) to.deleteRecursively()
                    if (from.exists()) from.copyRecursively(to, true)
                    link.sendStatus(0, null)
                }
                "DLMessagePurgeDiskSpace" -> link.sendStatus(-1, "Operation not supported")
                "DLMessageDisconnect" -> throw IOException("The device ended the backup without a result")
                "DLMessageProcessMessage" -> {
                    val r = msg.getOrNull(1) as? Map<*, *> ?: emptyMap<Any, Any>()
                    val code = (r["ErrorCode"] as? Number)?.toLong() ?: 0
                    if (code == 0L) return
                    throw IOException((r["ErrorDescription"] as? String ?: "Backup failed") + " (error $code)")
                }
                else -> {
                    onEvent("(unhandled device request $name)")
                    link.sendStatus(-1, "Operation not supported")
                }
            }
        }
    }

    /** A path from the device, kept inside the backup root. */
    private fun resolve(path: String?): File {
        val p = path ?: throw IOException("Missing path")
        val parts = p.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw IOException("Refusing path outside the backup: $p")
        return parts.fold(root) { f, part -> File(f, part) }
    }

    private fun u32(n: Int) = ByteBuffer.allocate(4).putInt(n).array()

    /** DLMessageDownloadFiles: send each requested file, or an error for ones we don't have. */
    private fun sendFiles(paths: List<*>) {
        val errors = LinkedHashMap<String, Any>()
        for (p in paths) {
            cancelCheck()
            val path = p as? String ?: continue
            val nameBytes = path.toByteArray(Charsets.UTF_8)
            link.conn.write(u32(nameBytes.size) + nameBytes)
            val f = runCatching { resolve(path) }.getOrNull()
            if (f == null || !f.isFile) {
                val msg = "No such file or directory"
                val b = msg.toByteArray()
                link.conn.write(u32(b.size + 1) + byteArrayOf(CODE_ERROR_LOCAL.toByte()) + b)
                errors[path] = mapOf("DLFileErrorString" to msg, "DLFileErrorCode" to ERR_NOT_FOUND)
                continue
            }
            f.inputStream().use { input ->
                val buf = ByteArray(BLOCK)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    link.conn.write(u32(n + 1) + byteArrayOf(CODE_FILE_DATA.toByte()) + buf.copyOf(n))
                }
            }
            link.conn.write(u32(1) + byteArrayOf(CODE_SUCCESS.toByte()))
        }
        link.conn.write(u32(0))
        if (errors.isEmpty()) link.sendStatus(0, null) else link.sendStatus(ERR_MULTI, "Multi status", errors)
    }

    /** DLMessageUploadFiles: receive files until a zero length. */
    private fun receiveFiles() {
        while (true) {
            cancelCheck()
            val dlen = link.readU32(WAIT_MS)
            if (dlen == 0L) break
            link.conn.readFully(dlen.toInt(), WAIT_MS) // the device-side name; we use the backup path below
            val flen = link.readU32(WAIT_MS)
            val path = String(link.conn.readFully(flen.toInt(), WAIT_MS), Charsets.UTF_8)
            val target = resolve(path)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            var blockLen = link.readU32(WAIT_MS)
            var code = link.conn.readFully(1, WAIT_MS)[0].toInt() and 0xFF
            var ok = true
            tmp.outputStream().buffered(BLOCK).use { out ->
                while (code == CODE_FILE_DATA) {
                    var left = blockLen - 1
                    while (left > 0) {
                        val n = minOf(left, BLOCK.toLong()).toInt()
                        out.write(link.conn.readFully(n, WAIT_MS))
                        stats.bytesReceived += n
                        left -= n
                    }
                    blockLen = link.readU32(WAIT_MS)
                    code = link.conn.readFully(1, WAIT_MS)[0].toInt() and 0xFF
                }
            }
            if (code == CODE_ERROR_REMOTE || code == CODE_ERROR_LOCAL) {
                val err = if (blockLen > 1) String(link.conn.readFully((blockLen - 1).toInt(), WAIT_MS)) else "unknown error"
                onEvent("  device couldn't send $path: $err")
                ok = false
            } else if (code != CODE_SUCCESS) {
                throw IOException("Unexpected transfer code 0x%02x for $path".format(code))
            }
            if (ok) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) { tmp.copyTo(target, true); tmp.delete() }
                stats.filesReceived++
            } else tmp.delete()
        }
        link.sendStatus(0, null)
    }

    private fun contentsOf(path: String): Map<String, Any> {
        val dir = resolve(path)
        val out = LinkedHashMap<String, Any>()
        for (f in dir.listFiles().orEmpty()) {
            out[f.name] = mapOf(
                "DLFileType" to when { f.isDirectory -> "DLFileTypeDirectory"; f.isFile -> "DLFileTypeRegular"; else -> "DLFileTypeUnknown" },
                "DLFileSize" to f.length(),
                "DLFileModificationDate" to Date(f.lastModified()),
            )
        }
        return out
    }
}
