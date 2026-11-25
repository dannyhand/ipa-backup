package us.dannyhand.ipabackup.service

import us.dannyhand.ipabackup.plist.Plist
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/** A byte pipe to a device service: either a raw mux stream or TLS on top of one. */
interface ByteConnection : Closeable {
    fun write(data: ByteArray)
    fun readFully(n: Int, timeoutMs: Long = 10_000): ByteArray
}

/** ByteConnection over plain streams (used for the TLS layer's decrypted streams). */
class StreamConnection(input: InputStream, private val output: OutputStream, private val onClose: () -> Unit = {}) : ByteConnection {
    private val input = DataInputStream(input)
    override fun write(data: ByteArray) { output.write(data); output.flush() }
    override fun readFully(n: Int, timeoutMs: Long): ByteArray = ByteArray(n).also { input.readFully(it) }
    override fun close() = onClose()
}

/** The framing lockdownd and most services use: u32 big-endian length, then a plist. */
object PlistService {
    fun send(c: ByteConnection, message: Map<String, Any?>) {
        val body = Plist.toXml(message).toByteArray(Charsets.UTF_8)
        c.write(ByteBuffer.allocate(4 + body.size).putInt(body.size).put(body).array())
    }

    @Suppress("UNCHECKED_CAST")
    fun receive(c: ByteConnection, timeoutMs: Long = 10_000): Map<String, Any?> {
        val len = ByteBuffer.wrap(c.readFully(4, timeoutMs)).int
        if (len <= 0 || len > (16 shl 20)) throw IOException("Bad plist message length $len")
        return Plist.parse(c.readFully(len, timeoutMs)) as? Map<String, Any?>
            ?: throw IOException("Reply was not a dict")
    }
}
