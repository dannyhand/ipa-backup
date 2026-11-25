package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.ITunesDb
import us.dannyhand.ipabackup.music.MusicLibrary
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/** Builds iTunesDB bytes following the iPodLinux / libgpod layout and parses them back. */
class ITunesDbTest {
    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun mhod(type: Int, text: String, utf8: Boolean = false): ByteArray {
        val s = text.toByteArray(if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
        val b = le(40 + s.size)
        b.put("mhod".toByteArray()).putInt(0x18).putInt(40 + s.size).putInt(type).putInt(0).putInt(0)
        b.putInt(if (utf8) 2 else 1).putInt(s.size).putInt(1).putInt(0).put(s)
        return b.array()
    }

    private fun mhit(id: Int, mediaType: Int, track: Int, year: Int, size: Int, vararg mhods: ByteArray): ByteArray {
        val header = 0x184 // a newer header that includes the media type at 0xD0
        val body = mhods.fold(ByteArray(0)) { a, b -> a + b }
        val h = le(header)
        h.put("mhit".toByteArray()).putInt(header).putInt(header + body.size).putInt(mhods.size).putInt(id)
        h.putInt(36, size).putInt(44, track).putInt(48, 12).putInt(52, year).putInt(92, 1).putInt(96, 2)
        h.putInt(0xD0, mediaType)
        return h.array() + body
    }

    private fun db(vararg tracks: ByteArray): ByteArray {
        val items = tracks.fold(ByteArray(0)) { a, b -> a + b }
        val mhlt = le(0x5C).put("mhlt".toByteArray()).putInt(0x5C).putInt(tracks.size).array() + items
        val mhsdTrack = le(0x60).put("mhsd".toByteArray()).putInt(0x60).putInt(0x60 + mhlt.size).putInt(1).array() + mhlt
        val mhsdPlaylists = le(0x60).put("mhsd".toByteArray()).putInt(0x60).putInt(0x60).putInt(2).array()
        val body = mhsdPlaylists + mhsdTrack // track list not first, to check we look for type 1
        val hdr = le(0xBC)
        hdr.put("mhbd".toByteArray()).putInt(0xBC).putInt(0xBC + body.size).putInt(1).putInt(0x30).putInt(2)
        return hdr.array() + body
    }

    private val sample = db(
        mhit(1, 1, 3, 2007, 5_000_000,
            mhod(1, "Churches and Serial Killers"), mhod(4, "Emery"), mhod(3, "I'm Only a Man"), mhod(5, "Rock"),
            mhod(22, "Emery"), mhod(12, "Toby Morrell"), mhod(2, ":iTunes_Control:Music:F07:ABCD.m4a")),
        mhit(2, 4, 0, 2008, 20_000_000, mhod(1, "Ep 1", utf8 = true), mhod(3, "My Show"), mhod(2, ":iTunes_Control:Music:F01:EFGH.mp3")),
        mhit(3, 1, 0, 0, 100, mhod(1, "Ghost")), // no location: not on the device
    )

    @Test
    fun parsesPlainDb() {
        val tracks = ITunesDb.parse(sample)
        assertEquals(2, tracks.size)
        val song = tracks.first { it.kind == MusicLibrary.Kind.MUSIC }
        assertEquals("Churches and Serial Killers", song.title)
        assertEquals("Emery", song.artist)
        assertEquals("I'm Only a Man", song.album)
        assertEquals("Rock", song.genre)
        assertEquals("Toby Morrell", song.composer)
        assertEquals("/iTunes_Control/Music/F07/ABCD.m4a", song.devicePath)
        assertEquals(3, song.trackNumber); assertEquals(12, song.trackCount); assertEquals(2007, song.year)
        assertEquals(1, song.discNumber); assertEquals(2, song.discCount)
        assertEquals(5_000_000L, song.size)
        assertEquals("Emery - Churches and Serial Killers.m4a", MusicLibrary.saveName(song))
        val ep = tracks.first { it.kind == MusicLibrary.Kind.PODCAST }
        assertEquals("Ep 1", ep.title) // UTF-8 encoded mhod
        assertEquals("My Show - Ep 1.mp3", MusicLibrary.saveName(ep))
    }

    @Test
    fun parsesCompressedCdb() {
        val header = ByteBuffer.wrap(sample, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val def = Deflater(9)
        def.setInput(sample, header, sample.size - header); def.finish()
        val out = ByteArrayOutputStream(); out.write(sample, 0, header)
        val buf = ByteArray(4096)
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        val cdb = out.toByteArray()
        assertTrue(cdb.size < sample.size)
        assertEquals(ITunesDb.parse(sample), ITunesDb.parse(cdb))
    }
}
