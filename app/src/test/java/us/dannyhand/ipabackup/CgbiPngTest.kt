package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.util.CgbiPng
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.random.Random

/** Strict reader for the converter's output (8-bit RGBA, filter 0): checks CRCs and real zlib framing. */
private fun readStandardPng(png: ByteArray): Triple<Int, Int, ByteArray> {
    val sig = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    check(png.copyOfRange(0, 8).contentEquals(sig))
    var pos = 8; var w = 0; var h = 0
    val idat = ByteArrayOutputStream()
    while (pos < png.size) {
        val len = ByteBuffer.wrap(png, pos, 4).int
        val type = String(png, pos + 4, 4)
        val body = png.copyOfRange(pos + 8, pos + 8 + len)
        val crc = ByteBuffer.wrap(png, pos + 8 + len, 4).int
        check(CRC32().apply { update(type.toByteArray()); update(body) }.value.toInt() == crc) { "bad CRC in $type" }
        check(type != "CgBI") { "CgBI chunk left in output" }
        when (type) {
            "IHDR" -> { val b = ByteBuffer.wrap(body); w = b.int; h = b.int; check(body[8].toInt() == 8 && body[9].toInt() == 6) }
            "IDAT" -> idat.write(body)
        }
        pos += 12 + len
    }
    val inf = java.util.zip.Inflater() // zlib framing required for standard PNGs
    inf.setInput(idat.toByteArray())
    val raw = ByteArray((w * 4 + 1) * h)
    var off = 0
    while (off < raw.size) { val n = inf.inflate(raw, off, raw.size - off); check(n > 0 || !inf.needsInput()); off += n }
    val px = ByteArray(w * h * 4)
    for (y in 0 until h) {
        check(raw[y * (w * 4 + 1)].toInt() == 0)
        System.arraycopy(raw, y * (w * 4 + 1) + 1, px, y * w * 4, w * 4)
    }
    return Triple(w, h, px)
}

class CgbiPngTest {
    private val w = 9
    private val h = 7

    /** Straight RGBA test pixels, including fully transparent and partially transparent ones. */
    private fun pixel(x: Int, y: Int): IntArray {
        val a = when { x == 0 -> 0; y == 0 -> 128; else -> 255 }
        return intArrayOf((x * 28) and 255, (y * 36) and 255, ((x + y) * 15) and 255, a)
    }

    /** Builds a CgBI PNG the way Xcode does: BGRA, premultiplied, raw deflate, CgBI chunk first. */
    private fun makeCgbi(filters: IntArray): ByteArray {
        val stride = w * 4
        val rows = Array(h) { y ->
            ByteArray(stride).also { line ->
                for (x in 0 until w) {
                    val (r, g, b, a) = pixel(x, y).toList()
                    fun pre(v: Int) = (v * a + 127) / 255
                    line[x * 4] = pre(b).toByte(); line[x * 4 + 1] = pre(g).toByte()
                    line[x * 4 + 2] = pre(r).toByte(); line[x * 4 + 3] = a.toByte()
                }
            }
        }
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) {
            val f = filters[y % filters.size]
            val cur = rows[y]
            val prev = if (y > 0) rows[y - 1] else ByteArray(stride)
            raw.write(f)
            for (i in 0 until stride) {
                val x = cur[i].toInt() and 255
                val a = if (i >= 4) cur[i - 4].toInt() and 255 else 0
                val b = prev[i].toInt() and 255
                val c = if (i >= 4) prev[i - 4].toInt() and 255 else 0
                val pred = when (f) {
                    0 -> 0; 1 -> a; 2 -> b; 3 -> (a + b) / 2
                    else -> { val p = a + b - c; val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c)
                        if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                }
                raw.write((x - pred) and 255)
            }
        }
        val def = Deflater(9, true) // raw deflate, no zlib header
        def.setInput(raw.toByteArray()); def.finish()
        val comp = ByteArrayOutputStream(); val buf = ByteArray(4096)
        while (!def.finished()) comp.write(buf, 0, def.deflate(buf))

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(t: String, body: ByteArray) {
            out.write(ByteBuffer.allocate(4).putInt(body.size).array()); out.write(t.toByteArray()); out.write(body)
            out.write(ByteBuffer.allocate(4).putInt(CRC32().apply { update(t.toByteArray()); update(body) }.value.toInt()).array())
        }
        chunk("CgBI", byteArrayOf(0x50, 0x00, 0x20, 0x02))
        chunk("IHDR", ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(6).put(0).put(0).put(0).array())
        chunk("IDAT", comp.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test
    fun convertsAllFilterTypesToStandardPng() {
        val cgbi = makeCgbi(intArrayOf(0, 1, 2, 3, 4))
        assertTrue(CgbiPng.isCgbi(cgbi))

        val (ow, oh, px) = readStandardPng(CgbiPng.toStandard(cgbi)!!)
        assertEquals(w, ow); assertEquals(h, oh)
        for (y in 0 until h) for (x in 0 until w) {
            val exp = pixel(x, y)
            val i = (y * w + x) * 4
            val got = IntArray(4) { px[i + it].toInt() and 255 }
            assertEquals("alpha at $x,$y", exp[3], got[3])
            if (exp[3] == 0) continue
            // Premultiplying loses precision at low alpha; allow a little rounding.
            val tol = if (exp[3] < 255) 2 else 0
            for (ch in 0..2) assertTrue("channel $ch at $x,$y: ${got[ch]} vs ${exp[ch]}", Math.abs(exp[ch] - got[ch]) <= tol)
        }
    }

    @Test
    fun ignoresNormalPngsAndGarbage() {
        assertNull(CgbiPng.toStandard(Random(1).nextBytes(100)))
        // A converted (standard) PNG is not CgBI, so it's left alone.
        val normal = CgbiPng.toStandard(makeCgbi(intArrayOf(0)))!!
        assertTrue(!CgbiPng.isCgbi(normal))
        assertNull(CgbiPng.toStandard(normal))
    }
}
