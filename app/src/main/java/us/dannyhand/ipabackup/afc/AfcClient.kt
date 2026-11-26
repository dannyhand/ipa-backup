package us.dannyhand.ipabackup.afc

import us.dannyhand.ipabackup.service.ByteConnection
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AfcException(val code: Long) : IOException("AFC error $code (${AfcClient.errorName(code)})")

/**
 * Apple File Conduit (com.apple.afc), rooted at /var/mobile/Media. No jailbreak needed: this is how
 * music, photos, voice memos and books are read. The jailbreak-only com.apple.afc2 speaks the same
 * protocol rooted at "/", and house_arrest switches a connection to AFC rooted at one app's sandbox.
 * See libimobiledevice src/afc.c. Every packet has a 40-byte little-endian header:
 *   "CFA6LPAA" magic, u64 entire_length, u64 this_length (header+params), u64 packet_num, u64 operation
 * followed by params and then payload (entire_length - this_length bytes).
 */
class AfcClient(private val conn: ByteConnection) {

    companion object {
        const val SERVICE = "com.apple.afc"
        private val MAGIC = "CFA6LPAA".toByteArray(Charsets.US_ASCII)
        private const val HEADER = 40

        const val OP_STATUS = 0x01L
        const val OP_DATA = 0x02L
        const val OP_READ_DIR = 0x03L
        const val OP_GET_FILE_INFO = 0x0AL
        const val OP_FILE_OPEN = 0x0DL
        const val OP_FILE_OPEN_RES = 0x0EL
        const val OP_FILE_READ = 0x0FL
        const val OP_FILE_CLOSE = 0x14L
        const val OP_REMOVE_PATH = 0x08L
        const val OP_MAKE_DIR = 0x09L
        const val OP_FILE_WRITE = 0x10L
        const val OP_RENAME_PATH = 0x18L

        const val MODE_RDONLY = 1L
        /** "w": write-only, create, truncate. */
        const val MODE_WRONLY = 3L
        /** libimobiledevice keeps writes to 32 KB per packet. */
        const val WRITE_CHUNK = 1 shl 15
        const val READ_CHUNK = 1 shl 16
        private const val MAX_PACKET = 8 shl 20

        fun errorName(code: Long) = when (code) {
            0L -> "success"; 1L -> "unknown"; 4L -> "invalid argument"; 7L -> "no resources"
            8L -> "not found"; 9L -> "is a directory"; 10L -> "permission denied"
            19L -> "operation timeout"; 22L -> "no space left"; else -> "?"
        }
    }

    private var packetNum = 0L

    /** Called before every chunk of a file copy; throw from it to abort (e.g. user pressed Stop). */
    @Volatile var cancelCheck: () -> Unit = {}

    private fun send(op: Long, params: ByteArray, payload: ByteArray? = null, payloadLen: Int = payload?.size ?: 0) {
        val thisLen = HEADER + params.size
        val bb = ByteBuffer.allocate(thisLen + payloadLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(MAGIC).putLong((thisLen + payloadLen).toLong()).putLong(thisLen.toLong()).putLong(packetNum++).putLong(op).put(params)
        if (payload != null) bb.put(payload, 0, payloadLen)
        conn.write(bb.array())
    }

    /** Returns (operation, bytes after the header). Throws AfcException on a non-zero STATUS. */
    private fun receive(timeoutMs: Long = 30_000): Pair<Long, ByteArray> {
        val h = ByteBuffer.wrap(conn.readFully(HEADER, timeoutMs)).order(ByteOrder.LITTLE_ENDIAN)
        for (i in MAGIC.indices) if (h.get(i) != MAGIC[i]) throw IOException("Bad AFC magic")
        val entire = h.getLong(8)
        val thisLen = h.getLong(16)
        val op = h.getLong(32)
        if (thisLen < HEADER || entire < thisLen || entire > MAX_PACKET) throw IOException("Bad AFC lengths $entire/$thisLen")
        val body = conn.readFully((entire - HEADER).toInt(), timeoutMs)
        if (op == OP_STATUS) {
            val code = if (body.size >= 8) le64(body, 0) else 0L
            if (code != 0L) throw AfcException(code)
        }
        return op to body
    }

    private fun le64(b: ByteArray, off: Int) = ByteBuffer.wrap(b, off, 8).order(ByteOrder.LITTLE_ENDIAN).long
    private fun cstr(s: String) = s.toByteArray(Charsets.UTF_8) + 0
    private fun longs(vararg v: Long) =
        ByteBuffer.allocate(8 * v.size).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putLong(it) } }.array()

    /** e.g. st_size, st_ifmt, st_mtime. */
    fun fileInfo(path: String): Map<String, String> {
        send(OP_GET_FILE_INFO, cstr(path))
        val (_, data) = receive()
        val parts = String(data, Charsets.UTF_8).split('\u0000')
        return buildMap { for (i in 0 until parts.size - 1 step 2) if (parts[i].isNotEmpty()) put(parts[i], parts[i + 1]) }
    }

    /** Directory entries, without "." and "..". */
    fun readDir(path: String): List<String> {
        send(OP_READ_DIR, cstr(path))
        val (_, data) = receive()
        return String(data, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() && it != "." && it != ".." }
    }

    data class Entry(val path: String, val isDir: Boolean, val isLink: Boolean, val size: Long, val mtimeMs: Long)

    fun stat(path: String): Entry {
        val info = fileInfo(path)
        val fmt = info["st_ifmt"] ?: ""
        return Entry(
            path = path,
            isDir = fmt == "S_IFDIR",
            isLink = fmt == "S_IFLNK",
            size = info["st_size"]?.toLongOrNull() ?: 0L,
            mtimeMs = (info["st_mtime"]?.toLongOrNull() ?: 0L) / 1_000_000L, // device reports nanoseconds
        )
    }

    fun open(path: String, mode: Long = MODE_RDONLY): Long {
        send(OP_FILE_OPEN, longs(mode) + cstr(path))
        val (op, data) = receive()
        if (op != OP_FILE_OPEN_RES || data.size < 8) throw IOException("Unexpected FILE_OPEN reply op=$op")
        return le64(data, 0)
    }

    /** Returns up to [length] bytes; an empty array means end of file. */
    fun read(handle: Long, length: Int = READ_CHUNK): ByteArray {
        send(OP_FILE_READ, longs(handle, length.toLong()))
        val (op, data) = receive()
        return if (op == OP_DATA) data else ByteArray(0)
    }

    fun close(handle: Long) {
        send(OP_FILE_CLOSE, longs(handle))
        receive()
    }

    /** Creates a directory (and, like mkdir -p, its parents). Succeeds if it already exists. */
    fun makeDir(path: String) {
        send(OP_MAKE_DIR, cstr(path))
        receive()
    }

    /** Renames or moves a file or folder (both paths on the same device). */
    fun rename(from: String, to: String) {
        send(OP_RENAME_PATH, cstr(from) + cstr(to))
        receive()
    }

    /** Deletes a file or an empty directory. */
    fun remove(path: String) {
        send(OP_REMOVE_PATH, cstr(path))
        receive()
    }

    /** Deletes a file, or a folder and everything in it. Returns the number of files removed. */
    fun removeTree(path: String): Int {
        val e = stat(path)
        var n = 0
        if (e.isDir) {
            for (name in readDir(path)) n += removeTree(if (path.endsWith("/")) path + name else "$path/$name")
        } else {
            n = 1
        }
        remove(path)
        return n
    }

    /** Uploads [input] to [path] (created or truncated). [progress] gets (bytesWritten, [total]). */
    fun writeFile(path: String, input: java.io.InputStream, total: Long = -1, progress: (Long, Long) -> Unit = { _, _ -> }): Long {
        cancelCheck()
        val handle = open(path, MODE_WRONLY)
        var written = 0L
        try {
            val buf = ByteArray(WRITE_CHUNK)
            while (true) {
                cancelCheck()
                var n = 0
                while (n < buf.size) { // fill the chunk, so packets stay a steady size
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n == 0) break
                send(OP_FILE_WRITE, longs(handle), buf, n)
                receive()
                written += n
                progress(written, total)
                if (n < buf.size) break
            }
        } finally {
            runCatching { close(handle) }
        }
        return written
    }

    /** Reads up to [max] bytes from the start of a file (e.g. a JPEG's EXIF header). */
    fun readHead(path: String, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val handle = open(path)
        try {
            while (out.size() < max) {
                cancelCheck()
                val chunk = read(handle, minOf(READ_CHUNK, max - out.size()))
                if (chunk.isEmpty()) break
                out.write(chunk)
            }
        } finally {
            runCatching { close(handle) }
        }
        return out.toByteArray()
    }

    /** Copies a device file to [out]. [progress] gets (bytesCopied, totalBytes or -1). Returns bytes copied. */
    fun copyFile(path: String, out: OutputStream, progress: (Long, Long) -> Unit = { _, _ -> }): Long {
        cancelCheck()
        val total = fileInfo(path)["st_size"]?.toLongOrNull() ?: -1L
        val handle = open(path)
        var copied = 0L
        try {
            while (true) {
                cancelCheck()
                val chunk = read(handle)
                if (chunk.isEmpty()) break
                out.write(chunk)
                copied += chunk.size
                progress(copied, total)
                if (total in 0..copied) break
            }
        } finally {
            runCatching { close(handle) }
        }
        if (total >= 0 && copied != total) throw IOException("Short copy: $copied of $total bytes")
        return copied
    }
}
