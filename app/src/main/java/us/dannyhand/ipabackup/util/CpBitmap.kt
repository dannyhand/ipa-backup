package us.dannyhand.ipabackup.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Apple's .cpbitmap wallpaper format (/var/mobile/Library/SpringBoard/LockBackground.cpbitmap and
 * HomeBackground.cpbitmap): raw 32-bit pixels in B,G,R,A order, rows possibly padded, followed by a
 * 20-byte footer whose first two little-endian ints are width and height. The row length is worked out
 * from the file size, so padding to any multiple doesn't matter.
 */
object CpBitmap {
    class Image(val width: Int, val height: Int, val argb: IntArray)

    fun decode(data: ByteArray): Image? {
        if (data.size < 24) return null
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val w = bb.getInt(data.size - 20)
        val h = bb.getInt(data.size - 16)
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
        val body = data.size - 20
        if (body % h != 0) return null
        val stride = body / h
        if (stride < w * 4) return null
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
