package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.util.AudioTagger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class AudioTaggerTest {
    private val L1 = Charsets.ISO_8859_1
    private val tags = AudioTagger.Tags("Song", "Band", "Record", "Band", "Rock", "Writer", 2007, 3, 12, 1, 2)

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val size = 8 + parts.sumOf { it.size }
        return ByteBuffer.allocate(size).putInt(size).put(type.toByteArray(L1)).apply { parts.forEach { put(it) } }.array()
    }
    private fun ints(vararg v: Int) = ByteBuffer.allocate(4 * v.size).apply { v.forEach { putInt(it) } }.array()
    private fun longs(vararg v: Long) = ByteBuffer.allocate(8 * v.size).apply { v.forEach { putLong(it) } }.array()

    private val markers = listOf("AUDIO-CHUNK-ONE!", "AUDIO-CHUNK-TWO!").map { it.toByteArray() }

    /** ftyp, moov (before mdat, like iTunes files), mdat holding two chunks the offsets point at. */
    private fun makeMp4(udta: ByteArray?, co64: Boolean = false): ByteArray {
        val ftyp = box("ftyp", "M4A ".toByteArray(), ints(0), "M4A mp42isom".toByteArray())
        fun build(off1: Long, off2: Long): ByteArray {
            val table = if (co64) box("co64", ints(0, 2), longs(off1, off2)) else box("stco", ints(0, 2, off1.toInt(), off2.toInt()))
            val trak = box("trak", box("mdia", box("minf", box("stbl", table))))
            return if (udta == null) box("moov", box("mvhd", ByteArray(100)), trak)
            else box("moov", box("mvhd", ByteArray(100)), trak, udta)
        }
        val moovSize = build(0, 0).size
        val mdatStart = ftyp.size + moovSize + 8
        val gap = ByteArray(37) { 7 }
        val moov = build(mdatStart.toLong(), (mdatStart + markers[0].size + gap.size).toLong())
        val mdat = box("mdat", markers[0], gap, markers[1])
        return ftyp + moov + mdat
    }

    private class B(val type: String, val start: Int, val size: Int)
    private fun kids(b: ByteArray, from: Int, to: Int): List<B> {
        val l = ArrayList<B>(); var p = from
        while (p + 8 <= to) { val s = ByteBuffer.wrap(b, p, 4).int; l += B(String(b, p + 4, 4, L1), p, s); p += s }
        return l
    }
    private fun find(b: ByteArray, path: List<String>, from: Int = 0, to: Int = b.size): B? {
        val k = kids(b, from, to).firstOrNull { it.type == path[0] } ?: return null
        if (path.size == 1) return k
        val inner = k.start + 8 + if (k.type == "meta") 4 else 0
        return find(b, path.drop(1), inner, k.start + k.size)
    }
    private fun offsets(b: ByteArray): List<Long> {
        val base = listOf("moov", "trak", "mdia", "minf", "stbl")
        find(b, base + "stco")?.let { s -> return List(ByteBuffer.wrap(b).getInt(s.start + 12)) { i -> ByteBuffer.wrap(b).getInt(s.start + 16 + 4 * i).toLong() } }
        val c = find(b, base + "co64")!!
        return List(ByteBuffer.wrap(b).getInt(c.start + 12)) { i -> ByteBuffer.wrap(b).getLong(c.start + 16 + 8 * i) }
    }
    private fun ilstText(b: ByteArray, type: String): String? {
        val item = find(b, listOf("moov", "udta", "meta", "ilst", type)) ?: return null
        val data = item.start + 8
        return String(b, data + 16, ByteBuffer.wrap(b).getInt(data) - 16, Charsets.UTF_8)
    }
    private fun assertChunksIntact(out: ByteArray) {
        offsets(out).zip(markers).forEach { (off, m) -> assertArrayEquals(m, out.copyOfRange(off.toInt(), off.toInt() + m.size)) }
    }

    private fun tag(input: ByteArray, ext: String = "m4a"): Pair<AudioTagger.Result, ByteArray> {
        val f = File.createTempFile("tag", ".$ext").apply { writeBytes(input); deleteOnExit() }
        val out = ByteArrayOutputStream()
        return AudioTagger.writeTagged(f, ext, tags, out) to out.toByteArray()
    }

    @Test
    fun addsTagsAndShiftsOffsetsWhenFileHasNoMetadata() {
        val input = makeMp4(udta = null)
        val (r, out) = tag(input)
        assertEquals(AudioTagger.Result.MP4_TAGGED, r)
        assertTrue(out.size > input.size)
        assertChunksIntact(out)
        assertEquals("Song", ilstText(out, "©nam"))
        assertEquals("Band", ilstText(out, "©ART"))
        assertEquals("Record", ilstText(out, "©alb"))
        assertEquals("2007", ilstText(out, "©day"))
    }

    @Test
    fun usesItunesPaddingAndKeepsOtherItems() {
        val lyrics = box("©lyr", box("data", ints(1, 0), "la la la".toByteArray()))
        val hdlr = box("hdlr", ints(0, 0), "mdirappl".toByteArray(L1), ints(0, 0), byteArrayOf(0, 0))
        val udta = box("udta", box("meta", ints(0), hdlr, box("ilst", lyrics), box("free", ByteArray(4088))))
        val input = makeMp4(udta)
        val (_, out) = tag(input)
        assertEquals("file size unchanged: tags went into the padding", input.size, out.size)
        assertChunksIntact(out)
        assertEquals("la la la", ilstText(out, "©lyr"))
        assertEquals("Song", ilstText(out, "©nam"))
    }

    @Test
    fun shiftsCo64Offsets() {
        val (_, out) = tag(makeMp4(udta = null, co64 = true))
        assertChunksIntact(out)
    }

    @Test
    fun retaggingReplacesInsteadOfDuplicating() {
        val (_, once) = tag(makeMp4(udta = null))
        val (_, twice) = tag(once)
        assertEquals(once.size, twice.size)
        val ilst = find(twice, listOf("moov", "udta", "meta", "ilst"))!!
        assertEquals(1, kids(twice, ilst.start + 8, ilst.start + ilst.size).count { it.type == "©nam" })
    }

    @Test
    fun mp3GetsId3OnlyWhenMissing() {
        val audio = ByteArray(1000) { (it * 7).toByte() }
        val (r1, out1) = tag(audio, "mp3")
        assertEquals(AudioTagger.Result.ID3_ADDED, r1)
        assertEquals("ID3", String(out1, 0, 3))
        assertArrayEquals(audio, out1.copyOfRange(out1.size - audio.size, out1.size))
        val (r2, out2) = tag(out1, "mp3")
        assertEquals(AudioTagger.Result.ID3_KEPT, r2)
        assertArrayEquals(out1, out2)
    }

    @Test
    fun otherFormatsUnchanged() {
        val data = ByteArray(500) { it.toByte() }
        val (r, out) = tag(data, "m4p")
        assertEquals(AudioTagger.Result.UNCHANGED, r)
        assertArrayEquals(data, out)
    }
}
