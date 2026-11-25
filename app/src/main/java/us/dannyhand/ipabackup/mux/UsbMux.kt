package us.dannyhand.ipabackup.mux

import us.dannyhand.ipabackup.usb.AppleUsbTransport
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Device-side usbmux protocol — what usbmuxd speaks to the phone over USB
 * (see usbmuxd/src/device.c). Every packet starts with a big-endian mux header:
 *
 *   v1 (8 bytes):  u32 protocol, u32 length
 *   v2 (16 bytes): u32 protocol, u32 length, u32 magic 0xFEEDFACE, u16 tx_seq, u16 rx_seq
 *
 * `length` includes the header. Protocol 6 (TCP) packets carry a 20-byte TCP header,
 * which multiplexes connections to on-device services such as lockdownd (port 62078).
 */
class UsbMux(
    private val usb: AppleUsbTransport,
    private val log: (String) -> Unit = {},
) : Closeable {

    companion object {
        const val PROTO_VERSION = 0
        const val PROTO_CONTROL = 1
        const val PROTO_SETUP = 2
        const val PROTO_TCP = 6
        private const val MAGIC = 0xFEEDFACE.toInt()

        const val TH_FIN = 0x01
        const val TH_SYN = 0x02
        const val TH_RST = 0x04
        const val TH_ACK = 0x10

        const val RX_WINDOW = 131072
        const val TCP_HEADER_LEN = 20
        private const val MAX_PACKET = 1 shl 20
    }

    data class Packet(val proto: Int, val txSeq: Int, val rxSeq: Int, val payload: ByteArray)

    var version = 0
        private set
    /** Logs every mux packet header in/out. Turn off once the protocol is stable. */
    var debug = false
    private var txSeq = 0
    private var rxSeq = 0xFFFF
    private val writeLock = Any()
    private val reassembler = Reassembler()
    private val streams = ConcurrentHashMap<Int, MuxStream>()
    private var nextPort = 0x1000
    @Volatile private var running = false
    private var reader: Thread? = null

    private val headerLen get() = if (version >= 2) 16 else 8

    /** Version exchange (+ setup packet for v2). Must be called before [start]. */
    fun handshake() {
        // Throw away anything still queued from an earlier connection (seen after quick reconnects),
        // otherwise it gets mistaken for the reply to our version packet.
        val drained = drainStaleInput()
        if (drained > 0) log("Discarded $drained stale bytes from a previous connection")

        val vh = ByteBuffer.allocate(12).putInt(2).putInt(0).putInt(0).array() // major 2, minor 0
        sendPacket(PROTO_VERSION, vh, null)

        val deadline = System.currentTimeMillis() + 5000
        var pkt: Packet
        while (true) {
            val left = deadline - System.currentTimeMillis()
            pkt = (if (left > 0) readPacketBlocking(left) else null) ?: throw IOException("No version reply from device")
            if (pkt.proto == PROTO_VERSION && pkt.payload.size >= 8) break
            // Late packet from an old connection: ignore it and keep waiting.
        }
        val bb = ByteBuffer.wrap(pkt.payload)
        val major = bb.int
        val minor = bb.int
        version = major
        log("usbmux version $major.$minor")
        if (major !in 1..2) throw IOException("Unsupported usbmux version $major")
        if (version >= 2) sendPacket(PROTO_SETUP, null, byteArrayOf(0x07))
    }

    /** Starts the background reader that dispatches incoming packets to streams. */
    fun start() {
        running = true
        reader = Thread({
            val buf = ByteArray(AppleUsbTransport.MAX_TRANSFER)
            while (running) {
                val n = usb.read(buf, 200)
                if (n <= 0) continue
                reassembler.add(buf, n)
                while (true) {
                    val p = reassembler.next(headerLen) ?: break
                    try { dispatch(p) } catch (e: Exception) { log("dispatch error: ${e.message}") }
                }
            }
        }, "usbmux-reader").apply { isDaemon = true; start() }
    }

    /** Opens a mux TCP connection to [port] on the device. */
    fun connect(port: Int, timeoutMs: Long = 5000): MuxStream {
        check(running) { "call start() first" }
        val sport = synchronized(this) { nextPort++ }
        val s = MuxStream(this, sport, port)
        streams[sport] = s
        s.sendTcp(TH_SYN, null)
        try {
            s.awaitConnected(timeoutMs)
        } catch (e: Exception) {
            streams.remove(sport)
            throw e
        }
        return s
    }

    internal fun forget(stream: MuxStream) { streams.remove(stream.sport) }

    internal fun sendPacket(proto: Int, header: ByteArray?, data: ByteArray?) = synchronized(writeLock) {
        val hl = headerLen
        val total = hl + (header?.size ?: 0) + (data?.size ?: 0)
        val bb = ByteBuffer.allocate(total) // big-endian by default
        bb.putInt(proto).putInt(total)
        if (version >= 2) {
            bb.putInt(MAGIC)
            if (proto == PROTO_SETUP) {
                // usbmuxd resets the counters on SETUP, and SETUP itself consumes tx_seq 0.
                txSeq = 0
                rxSeq = 0xFFFF
            }
            bb.putShort(txSeq.toShort()).putShort(rxSeq.toShort())
            if (debug) log("→ mux proto=$proto len=$total tx=$txSeq rx=$rxSeq")
            txSeq = (txSeq + 1) and 0xFFFF
        }
        header?.let { bb.put(it) }
        data?.let { bb.put(it) }
        usb.write(bb.array())
    }

    private fun dispatch(p: Packet) {
        // Track the device's sequence number for our outgoing v2 headers.
        // (Compare with dev->rx_seq handling in usbmuxd's device.c if the device complains.)
        if (debug) log("← mux proto=${p.proto} payload=${p.payload.size} tx=${p.txSeq} rx=${p.rxSeq}")
        if (version >= 2) rxSeq = p.txSeq
        when (p.proto) {
            PROTO_TCP -> {
                if (p.payload.size < TCP_HEADER_LEN) return
                val bb = ByteBuffer.wrap(p.payload)
                val srcPort = bb.short.toInt() and 0xFFFF   // device's service port
                val dstPort = bb.short.toInt() and 0xFFFF   // our local port
                val seq = bb.int
                val ack = bb.int
                bb.get()                                     // data offset
                val flags = bb.get().toInt() and 0xFF
                val data = p.payload.copyOfRange(TCP_HEADER_LEN, p.payload.size)
                if (debug) log("   tcp $srcPort→$dstPort flags=0x%02x seq=$seq ack=$ack data=${data.size}".format(flags))
                val s = streams[dstPort]
                if (s == null) {
                    log("TCP packet for unknown port $dstPort (from $srcPort), flags=0x%02x".format(flags))
                    return
                }
                s.onTcp(seq, ack, flags, data)
            }
            PROTO_CONTROL -> {
                // Byte 0 is the message type (3 = error, 4 = warning, 7 = info); the rest is text.
                val type = p.payload.firstOrNull()?.toInt() ?: -1
                val kind = when (type) { 3 -> "ERROR"; 4 -> "warning"; 7 -> "info"; else -> "type $type" }
                val text = String(p.payload, 1, maxOf(0, p.payload.size - 1), Charsets.UTF_8).trimEnd('\u0000', '\n')
                log("Device $kind: $text")
            }
            else -> log("ignoring mux proto ${p.proto}")
        }
    }

    /** Synchronous read used only during the handshake, before the reader thread starts. */
    private fun readPacketBlocking(timeoutMs: Long): Packet? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(AppleUsbTransport.MAX_TRANSFER)
        while (System.currentTimeMillis() < deadline) {
            nextOrResync()?.let { return it }
            val n = usb.read(buf, 200)
            if (n > 0) reassembler.add(buf, n)
        }
        return nextOrResync()
    }

    /** During the handshake, garbage (a partial old packet) just gets dropped instead of failing. */
    private fun nextOrResync(): Packet? = try {
        reassembler.next(headerLen)
    } catch (e: IOException) {
        reassembler.clear()
        null
    }

    private fun drainStaleInput(): Int {
        val buf = ByteArray(AppleUsbTransport.MAX_TRANSFER)
        var total = 0
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            val n = usb.read(buf, 100)
            if (n <= 0) break
            total += n
        }
        reassembler.clear()
        return total
    }

    override fun close() {
        streams.values.toList().forEach { runCatching { it.close() } }
        running = false
        reader?.join(1000)
    }

    /** Collects USB reads (which may split or merge mux packets) into whole packets. */
    private class Reassembler {
        private var buf = ByteArray(65536)
        private var size = 0

        @Synchronized fun add(src: ByteArray, n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
            System.arraycopy(src, 0, buf, size, n)
            size += n
        }

        @Synchronized fun clear() { size = 0 }

        @Synchronized fun next(headerLen: Int): Packet? {
            if (size < 8) return null
            val bb = ByteBuffer.wrap(buf, 0, size)
            val proto = bb.getInt(0)
            val length = bb.getInt(4)
            if (length < headerLen || length > MAX_PACKET) throw IOException("Bad mux packet length $length")
            if (size < length) return null
            var tx = 0
            var rx = 0
            if (headerLen >= 16) {
                tx = bb.getShort(12).toInt() and 0xFFFF
                rx = bb.getShort(14).toInt() and 0xFFFF
            }
            val payload = buf.copyOfRange(headerLen, length)
            System.arraycopy(buf, length, buf, 0, size - length)
            size -= length
            return Packet(proto, tx, rx, payload)
        }
    }
}
