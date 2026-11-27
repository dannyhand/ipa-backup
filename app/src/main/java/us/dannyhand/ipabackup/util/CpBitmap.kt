package us.dannyhand.ipabackup.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Apple's .cpbitmap wallpaper format (/var/mobile/Library/SpringBoard/LockBackground.cpbitmap and
 * HomeBackground.cpbitmap): raw 32-bit pixels in B,G,R,A order, rows possibly padded to a multiple of
 * 8 pixels, then a footer. Width and height are little-endian numbers 20 and 16 bytes from the end.
 * On iOS 6 and older the footer is a small binary plist (about 70 bytes, starting "bplist00"), so the
 * pixels end where that begins; on newer iOS the footer is just the last 20 bytes.
 */
object CpBitmap {
    class Image(val width: Int, val height: Int, val argb: IntArray)

    fun decode(data: ByteArray): Image? {
        if (data.size < 24) return null
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        // Older footers store these as 16-bit values with 2 bytes between; read 16 bits so either layout works.
        val w = bb.getShort(data.size - 20).toInt() and 0xFFFF
        val h = bb.getShort(data.size - 16).toInt() and 0xFFFF
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
        val magic = "bplist00".toByteArray(Charsets.US_ASCII)
        var plistAt = -1
        for (i in data.size - magic.size downTo maxOf(0, data.size - 512)) {
            if ((magic.indices).all { data[i + it] == magic[it] }) { plistAt = i; break }
        }
        val body = if (plistAt >= 0) plistAt else data.size - 20
        val padded = (w + 7) / 8 * 8 * 4
        val stride = when {
            body % h == 0 && body / h >= w * 4 -> body / h
            padded * h <= body -> padded
            w * 4 * h <= body -> w * 4
            else -> return null
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            var o = y * stride
            for (x in 0 until w) {
                val b = data[o].toInt() and 0xFF
                val g = data[o + 1].toInt() and 0xFF
                val r = data[o + 2].toInt() and 0xFF
                // Wallpapers are opaque; ignore the alpha byte so a premultiplied/unused one can't blank the image.
                out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                o += 4
            }
        }
        return Image(w, h, out)
    }
}
