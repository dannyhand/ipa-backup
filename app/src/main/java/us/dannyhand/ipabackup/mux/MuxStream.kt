package us.dannyhand.ipabackup.mux

import us.dannyhand.ipabackup.service.ByteConnection
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One TCP-over-usbmux connection to a service port on the device. */
class MuxStream internal constructor(
    private val mux: UsbMux,
    val sport: Int,
    val dport: Int,
) : ByteConnection {

    private companion object {
        val EOF = ByteArray(0)
        const val CHUNK = 16000
    }

    private var txSeq = 0
    private var txAck = 0
    private val connected = CountDownLatch(1)
    @Volatile private var error: String? = null
    @Volatile private var closed = false
    private val inbox = LinkedBlockingQueue<ByteArray>()
    private var cur = EOF
    private var curPos = 0

    internal fun sendTcp(flags: Int, data: ByteArray?) = synchronized(this) {
        val h = ByteBuffer.allocate(UsbMux.TCP_HEADER_LEN)
            .putShort(sport.toShort())
            .putShort(dport.toShort())
            .putInt(txSeq)
            .putInt(txAck)
            .put(0x50.toByte())                          // data offset = 5 words
            .put(flags.toByte())
            .putShort((UsbMux.RX_WINDOW shr 8).toShort()) // window is sent >> 8
            .putShort(0)                                  // checksum (unused)
            .putShort(0)                                  // urgent pointer
            .array()
        mux.sendPacket(UsbMux.PROTO_TCP, h, data)
        if (data != null) txSeq += data.size
    }

    internal fun onTcp(seq: Int, ack: Int, flags: Int, payload: ByteArray) = synchronized(this) {
        when {
            flags and UsbMux.TH_RST != 0 -> {
                if (connected.count > 0) error = "Connection to port $dport refused"
                closed = true
                connected.countDown()
                inbox.offer(EOF)
            }
            flags == (UsbMux.TH_SYN or UsbMux.TH_ACK) -> {
                txSeq += 1          // our SYN consumed one sequence number
                txAck = seq + 1
                sendTcp(UsbMux.TH_ACK, null)
                connected.countDown()
            }
            payload.isNotEmpty() -> {
                txAck += payload.size
                inbox.offer(payload)
                sendTcp(UsbMux.TH_ACK, null)  // ack every packet; simple, keeps the device's window open
            }
        }
    }

    internal fun awaitConnected(timeoutMs: Long) {
        if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) throw IOException("Connect to port $dport timed out")
        error?.let { throw IOException(it) }
    }

    override fun write(data: ByteArray) {
        if (closed) throw IOException("Stream closed")
        var off = 0
        while (off < data.size) {
            val end = minOf(off + CHUNK, data.size)
            sendTcp(UsbMux.TH_ACK, data.copyOfRange(off, end))
            off = end
        }
    }

    override fun readFully(n: Int, timeoutMs: Long): ByteArray {
        val out = ByteArray(n)
        var got = 0
        while (got < n) {
            if (curPos >= cur.size) {
                val next = inbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
                    ?: throw IOException("Read timed out ($got/$n bytes)")
                if (next === EOF) throw IOException("Connection closed by device ($got/$n bytes)")
                cur = next
                curPos = 0
            }
            val take = minOf(n - got, cur.size - curPos)
            System.arraycopy(cur, curPos, out, got, take)
            curPos += take
            got += take
        }
        return out
    }

    /** Reads 1..len bytes, blocking for the first; returns -1 at end of stream. */
    fun readSome(b: ByteArray, off: Int, len: Int, timeoutMs: Long = 30_000): Int {
        if (len == 0) return 0
        if (curPos >= cur.size) {
            val next = inbox.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: throw IOException("Read timed out")
            if (next === EOF) { inbox.offer(EOF); return -1 }
            cur = next
            curPos = 0
        }
        val take = minOf(len, cur.size - curPos)
        System.arraycopy(cur, curPos, b, off, take)
        curPos += take
        return take
    }

    /** Stream views for layering TLS on top of this connection. */
    fun asInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (readSome(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int = readSome(b, off, len)
    }

    fun asOutputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = this@MuxStream.write(byteArrayOf(b.toByte()))
        override fun write(b: ByteArray, off: Int, len: Int) = this@MuxStream.write(b.copyOfRange(off, off + len))
    }

    override fun close() {
        if (!closed) {
            closed = true
            runCatching { sendTcp(UsbMux.TH_RST, null) } // usbmuxd closes connections with RST
        }
        mux.forget(this)
    }
}
