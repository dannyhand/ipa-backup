package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.util.AudioInfo
import us.dannyhand.ipabackup.util.AudioTagger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class AudioInfoTest {
    private fun tmp(bytes: ByteArray, ext: String) = File.createTempFile("audioinfo", ".$ext").apply { deleteOnExit(); writeBytes(bytes) }

    /** MPEG1 Layer III, 128 kbps, 44.1 kHz, stereo: 417-byte frames. */
    private fun frame(xingFrames: Int? = null): ByteArray {
        val f = ByteArray(417)
        f[0] = 0xFF.toByte(); f[1] = 0xFB.toByte(); f[2] = 0x90.toByte(); f[3] = 0x00
        if (xingFrames != null) {
            val o = 4 + 32
            "Xing".toByteArray().copyInto(f, o)
            ByteBuffer.wrap(f).putInt(o + 4, 1).putInt(o + 8, xingFrames)
        }
        return f
    }

    private fun id3v23(vararg frames: Pair<String, String>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, v) in frames) {
            val data = byteArrayOf(3) + v.toByteArray(Charsets.UTF_8) // UTF-8
            body.write(id.toByteArray()); body.write(ByteBuffer.allocate(4).putInt(data.size).array()); body.write(byteArrayOf(0, 0)); body.write(data)
        }
        val b = body.toByteArray()
        val s = b.size
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((s shr 21) and 0x7F).toByte(), ((s shr 14) and 0x7F).toByte(), ((s shr 7) and 0x7F).toByte(), (s and 0x7F).toByte()) + b
    }

    @Test fun mp3WithId3AndXing() {
        // 1000 frames × 1152 samples / 44100 Hz = 26.122 s
        val bytes = id3v23("TIT2" to "Señor Song", "TPE1" to "Band", "TALB" to "Record", "TRCK" to "3/12", "TPOS" to "1/2",
            "TYER" to "2007", "TCON" to "(17)") + frame(xingFrames = 1000) + frame() + frame()
        val i = AudioInfo.read(tmp(bytes, "mp3"))!!
        assertEquals(AudioInfo.Format.MP3, i.format)
        assertEquals("Señor Song", i.title); assertEquals("Band", i.artist); assertEquals("Record", i.album)
        assertEquals(3, i.track); assertEquals(12, i.trackCount); assertEquals(1, i.disc); assertEquals(2, i.discCount)
        assertEquals(2007, i.year); assertEquals("Rock", i.genre)
        assertEquals(26122L, i.durationMs); assertEquals(44100, i.sampleRate)
    }

    @Test fun cbrMp3WithoutTagEstimatesFromBitrate() {
        val bytes = ByteArray(0) + (1..100).flatMap { frame().toList() }.toByteArray()
        val i = AudioInfo.read(tmp(bytes, "mp3"))!!
        assertNull(i.title)
        assertEquals(128, i.bitrateKbps)
        assertEquals(41700L * 8 / 128, i.durationMs) // bytes*8/kbps = ms
    }

    @Test fun notAudio() {
        assertNull(AudioInfo.read(tmp("hello world, not audio at all".toByteArray(), "txt")))
    }

    private val L1 = Charsets.ISO_8859_1
    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val size = 8 + parts.sumOf { it.size }
        return ByteBuffer.allocate(size).putInt(size).put(type.toByteArray(L1)).apply { parts.forEach { put(it) } }.array()
    }
    private fun ints(vararg v: Int) = ByteBuffer.allocate(4 * v.size).apply { v.forEach { putInt(it) } }.array()

    @Test fun m4aTaggedByOurOwnTagger() {
        // mvhd v0: version/flags, created, modified, timescale 1000, duration 215500
        val mvhd = box("mvhd", ints(0, 0, 0, 1000, 215500), ByteArray(80))
        val hdlr = box("hdlr", ints(0, 0), "soun".toByteArray(L1), ByteArray(12))
        // stsd: version/flags, count 1, entry "alac": 6 reserved + 2 index, 8 reserved, 2 ch, 2 size, 4 reserved, rate 44100<<16
        val alac = box("alac", ByteArray(6), byteArrayOf(0, 1), ByteArray(8), byteArrayOf(0, 2, 0, 16), ByteArray(4), ints(44100 shl 16))
        val stbl = box("stbl", box("stsd", ints(0, 1), alac), box("stco", ints(0, 1, 0)))
        val trak = box("trak", box("mdia", hdlr, box("minf", stbl)))
        val raw = box("ftyp", "M4A ".toByteArray(L1), ints(0)) + box("moov", mvhd, trak) + box("mdat", ByteArray(64))
        val src = tmp(raw, "m4a")
        val out = ByteArrayOutputStream()
        AudioTagger.writeTagged(src, "m4a", AudioTagger.Tags("Title", "Artist", "Album", "Album Artist", "Jazz", "Composer", 1999, 4, 10, 2, 3), out)
        val i = AudioInfo.read(tmp(out.toByteArray(), "m4a"))!!
        assertEquals(AudioInfo.Format.ALAC, i.format)
        assertEquals("Title", i.title); assertEquals("Artist", i.artist); assertEquals("Album Artist", i.albumArtist)
        assertEquals("Composer", i.composer); assertEquals("Jazz", i.genre); assertEquals(1999, i.year)
        assertEquals(4, i.track); assertEquals(10, i.trackCount); assertEquals(2, i.disc); assertEquals(3, i.discCount)
        assertEquals(215500L, i.durationMs); assertEquals(44100, i.sampleRate)
        assertTrue(i.cover == null)
    }
}
