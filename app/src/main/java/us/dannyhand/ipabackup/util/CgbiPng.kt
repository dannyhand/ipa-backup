package us.dannyhand.ipabackup.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Converts Apple's "CgBI" iPhone-optimized PNGs (made by Xcode's pngcrush -iphone) into standard PNGs.
 * CgBI files have an extra CgBI chunk, raw deflate data without the zlib header, BGRA byte order
 * and premultiplied alpha, so ordinary decoders show them as blank or broken.
 */
object CgbiPng {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    fun isCgbi(png: ByteArray): Boolean =
        png.size > 16 && png.copyOfRange(0, 8).contentEquals(SIGNATURE) &&
            String(png, 12, 4, Charsets.US_ASCII) == "CgBI"

    /** Returns a standard PNG, or null if [png] isn't a CgBI PNG this converter handles (8-bit RGBA, not interlaced). */
    fun toStandard(png: ByteArray): ByteArray? {
        if (!isCgbi(png)) return null
        var pos = 8
        var width = 0
        var height = 0
        var ihdr: ByteArray? = null
        val idat = ByteArrayOutputStream()
        while (pos + 8 <= png.size) {
            val len = ByteBuffer.wrap(png, pos, 4).int
            val type = String(png, pos + 4, 4, Charsets.US_ASCII)
            if (len < 0 || pos + 12 + len > png.size) return null
            val body = png.copyOfRange(pos + 8, pos + 8 + len)
            pos += 12 + len
            when (type) {
                "IHDR" -> {
                    ihdr = body
                    val bb = ByteBuffer.wrap(body)
                    width = bb.int
                    height = bb.int
                    val bitDepth = body[8].toInt()
                    val colorType = body[9].toInt()
                    val interlace = body[12].toInt()
                    if (bitDepth != 8 || colorType != 6 || interlace != 0) return null
                }
                "IDAT" -> idat.write(body)
                "IEND" -> break
            }
        }
        if (ihdr == null || width <= 0 || height <= 0 || width > 8192 || height > 8192) return null

        val stride = width * 4
        val raw = ByteArray((stride + 1) * height)
        val inflater = Inflater(true) // raw deflate, no zlib header
        try {
            inflater.setInput(idat.toByteArray())
            var off = 0
            while (off < raw.size && !inflater.finished()) {
                val n = inflater.inflate(raw, off, raw.size - off)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                off += n
            }
            if (off < raw.size) return null
        } finally {
            inflater.end()
        }

        // Undo PNG row filters, then BGRA premultiplied -> RGBA straight. Output uses filter 0.
        val out = ByteArray((stride + 1) * height)
        val prev = ByteArray(stride)
        val line = ByteArray(stride)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            System.arraycopy(raw, y * (stride + 1) + 1, line, 0, stride)
            for (i in 0 until stride) {
                val a = if (i >= 4) line[i - 4].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= 4) prev[i - 4].toInt() and 0xFF else 0
                val x = line[i].toInt() and 0xFF
                line[i] = when (filter) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + ((a + b) ushr 1)
                    4 -> {
                        val p = a + b - c
                        val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c)
                        x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> return null
                }.toByte()
            }
            System.arraycopy(line, 0, prev, 0, stride)
            val o = y * (stride + 1)
            out[o] = 0
            for (px in 0 until width) {
                val i = px * 4
                val bl = line[i].toInt() and 0xFF
                val g = line[i + 1].toInt() and 0xFF
                val r = line[i + 2].toInt() and 0xFF
                val al = line[i + 3].toInt() and 0xFF
                fun un(v: Int) = if (al == 0) 0 else minOf(255, (v * 255 + al / 2) / al)
                out[o + 1 + i] = un(r).toByte()
                out[o + 1 + i + 1] = un(g).toByte()
                out[o + 1 + i + 2] = un(bl).toByte()
                out[o + 1 + i + 3] = al.toByte()
            }
        }

        val deflater = Deflater(Deflater.BEST_SPEED)
        val compressed = ByteArrayOutputStream()
        try {
            deflater.setInput(out)
            deflater.finish()
            val buf = ByteArray(65536)
            while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf))
        } finally {
            deflater.end()
        }

        val result = ByteArrayOutputStream()
        result.write(SIGNATURE)
        fun chunk(type: String, body: ByteArray) {
            val t = type.toByteArray(Charsets.US_ASCII)
            result.write(ByteBuffer.allocate(4).putInt(body.size).array())
            result.write(t)
            result.write(body)
            val crc = CRC32().apply { update(t); update(body) }
            result.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        chunk("IHDR", ihdr)
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", ByteArray(0))
        return result.toByteArray()
    }
}
