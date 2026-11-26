package us.dannyhand.ipabackup.backup

import us.dannyhand.ipabackup.plist.BinaryPlistWriter
import us.dannyhand.ipabackup.plist.Plist
import us.dannyhand.ipabackup.service.ByteConnection
import java.io.IOException
import java.nio.ByteBuffer

/**
 * DeviceLink: the messaging layer under mobilebackup2 (and iTunes' other sync services). Each message
 * is a plist array whose first item names it ("DLMessageVersionExchange", "DLMessageProcessMessage"…),
 * sent as a 4-byte big-endian length then a binary plist. File contents travel outside plists as raw
 * length-prefixed blocks (see MobileBackup2). See libimobiledevice's device_link_service.c.
 */
class DeviceLink(val conn: ByteConnection) {
    fun send(vararg items: Any?) {
        val body = BinaryPlistWriter.write(items.toList())
        conn.write(ByteBuffer.allocate(4).putInt(body.size).array() + body)
    }

    fun receive(timeoutMs: Long): List<Any?> {
        val len = readU32(timeoutMs)
        if (len <= 0 || len > (64 shl 20)) throw IOException("Bad DeviceLink message length $len")
        return Plist.parse(conn.readFully(len.toInt(), timeoutMs)) as? List<Any?> ?: throw IOException("DeviceLink message isn't an array")
    }

    fun readU32(timeoutMs: Long): Long = ByteBuffer.wrap(conn.readFully(4, timeoutMs)).int.toLong() and 0xFFFFFFFFL

    /** Device says which protocol version it speaks; we accept it and wait until it's ready. */
    fun versionExchange(timeoutMs: Long = 30_000) {
        val hello = receive(timeoutMs)
        if (hello.firstOrNull() != "DLMessageVersionExchange") throw IOException("Expected a version exchange, got ${hello.firstOrNull()}")
        val major = (hello.getOrNull(1) as? Number)?.toLong() ?: 300
        if (major > 300) throw IOException("DeviceLink version $major is newer than supported (300)")
        send("DLMessageVersionExchange", "DLVersionsOk", 300L)
        val ready = receive(timeoutMs)
        if (ready.firstOrNull() != "DLMessageDeviceReady") throw IOException("Device not ready: ${ready.firstOrNull()}")
    }

    fun sendProcessMessage(message: Map<String, Any?>) = send("DLMessageProcessMessage", message)

    fun receiveProcessMessage(timeoutMs: Long): Map<*, *> {
        val m = receive(timeoutMs)
        if (m.firstOrNull() != "DLMessageProcessMessage") throw IOException("Expected a process message, got ${m.firstOrNull()}")
        return m.getOrNull(1) as? Map<*, *> ?: throw IOException("Empty process message")
    }

    /** Reply to a device request: code 0 = OK; [detail] is the result (a dict, a number…). */
    fun sendStatus(code: Long, description: String?, detail: Any = emptyMap<String, Any>()) =
        send("DLMessageStatusResponse", code, description ?: "___EmptyParameterString___", detail)

    fun disconnect() = runCatching { send("DLMessageDisconnect", "___EmptyParameterString___") }
}
