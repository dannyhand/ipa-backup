package us.dannyhand.ipabackup.usb

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.Closeable
import java.io.IOException

/**
 * Raw bulk-endpoint access to an iOS device's usbmux interface
 * (class 0xFF / subclass 0xFE / protocol 2), via Android's USB Host API. No root needed.
 */
class AppleUsbTransport private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
) : Closeable {

    companion object {
        const val APPLE_VENDOR_ID = 0x05AC
        private const val MUX_CLASS = 0xFF
        private const val MUX_SUBCLASS = 0xFE
        private const val MUX_PROTOCOL = 2
        private const val WRITE_TIMEOUT_MS = 5000
        /** bulkTransfer() caps a single transfer at 16 KiB before Android 9. */
        const val MAX_TRANSFER = 16384

        fun isApple(device: UsbDevice) = device.vendorId == APPLE_VENDOR_ID

        /** Apple devices expose several USB configurations; the mux interface is usually in the last one. */
        fun findMuxInterface(device: UsbDevice): Pair<UsbConfiguration, UsbInterface>? {
            for (ci in device.configurationCount - 1 downTo 0) {
                val cfg = device.getConfiguration(ci)
                for (ii in 0 until cfg.interfaceCount) {
                    val itf = cfg.getInterface(ii)
                    if (itf.interfaceClass == MUX_CLASS &&
                        itf.interfaceSubclass == MUX_SUBCLASS &&
                        itf.interfaceProtocol == MUX_PROTOCOL
                    ) return cfg to itf
                }
            }
            return null
        }

        fun open(manager: UsbManager, device: UsbDevice, log: (String) -> Unit = {}): AppleUsbTransport {
            val (cfg, itf) = findMuxInterface(device)
                ?: throw IOException("No usbmux interface found on ${device.deviceName}")
            val conn = manager.openDevice(device) ?: throw IOException("openDevice failed (permission?)")
            try {
                // Switching config may fail if it's already active or the kernel holds an interface; try anyway.
                if (!conn.setConfiguration(cfg)) log("setConfiguration(${cfg.id}) returned false; continuing")
                if (!conn.claimInterface(itf, true)) throw IOException("claimInterface failed")

                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (i in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(i)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp == null || outEp == null) throw IOException("Bulk endpoints not found")
                log("Claimed mux interface ${itf.id} (cfg ${cfg.id}), IN 0x%02x / OUT 0x%02x"
                    .format(inEp.address, outEp.address))
                return AppleUsbTransport(conn, itf, inEp, outEp)
            } catch (e: Exception) {
                conn.close()
                throw e
            }
        }
    }

    private val writeLock = Any()

    fun write(data: ByteArray) = synchronized(writeLock) {
        var off = 0
        while (off < data.size) {
            val len = minOf(MAX_TRANSFER, data.size - off)
            val n = conn.bulkTransfer(epOut, data, off, len, WRITE_TIMEOUT_MS)
            if (n < 0) throw IOException("bulk OUT failed at offset $off")
            off += n
        }
        // A transfer that's an exact multiple of the packet size must be terminated with a zero-length packet.
        if (data.isNotEmpty() && data.size % epOut.maxPacketSize == 0) {
            conn.bulkTransfer(epOut, ByteArray(0), 0, WRITE_TIMEOUT_MS)
        }
    }

    /** Returns bytes read, or -1 on timeout/error (Android doesn't distinguish the two). */
    fun read(buf: ByteArray, timeoutMs: Int): Int =
        conn.bulkTransfer(epIn, buf, minOf(buf.size, MAX_TRANSFER), timeoutMs)

    override fun close() {
        try { conn.releaseInterface(iface) } catch (_: Exception) {}
        conn.close()
    }
}
