package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.MusicLibrary
import us.dannyhand.ipabackup.music.SongImport
import us.dannyhand.ipabackup.music.SongImport.Status
import us.dannyhand.ipabackup.util.AudioInfo
import kotlin.random.Random

class SongImportTest {
    private fun info(title: String?, artist: String?, album: String?, format: AudioInfo.Format = AudioInfo.Format.MP3) =
        AudioInfo.Info(format, title, artist, album, null, null, null, 0, 0, 0, 0, 0, 180_000, 192, 44100, null)

    @Test fun classifiesFormats() {
        assertEquals(Status.READY, SongImport.classify("mp3", info("a", "b", "c")))
        assertEquals(Status.READY, SongImport.classify("m4a", info("a", "b", "c", AudioInfo.Format.ALAC)))
        assertEquals(Status.DRM, SongImport.classify("m4p", info("a", "b", "c", AudioInfo.Format.PROTECTED_AAC)))
        assertEquals(Status.UNSUPPORTED, SongImport.classify("flac", null))
        assertEquals(Status.UNSUPPORTED, SongImport.classify("ogg", null))
        assertEquals(Status.READY, SongImport.classify("wav", null))
        assertEquals(Status.UNREADABLE, SongImport.classify("mp3", null))
    }

    @Test fun matchKeyIgnoresCasePunctuationAndThe() {
        assertEquals(SongImport.matchKey("Help!", "The Beatles"), SongImport.matchKey("help", "beatles"))
        assertNotEquals(SongImport.matchKey("Help!", "The Beatles", "Help!"), SongImport.matchKey("Help!", "The Beatles", "1"))
    }

    @Test fun marksDuplicatesAgainstLibraryAndWithinBatch() {
        val lib = listOf(MusicLibrary.Track(1, "Yesterday", "The Beatles", "Help!", "/iTunes_Control/Music/F00/AAAA.mp3", 1, 13))
        fun cand(name: String, i: AudioInfo.Info?) = SongImport.Candidate(name, "mp3", 1, i, SongImport.classify("mp3", i))
        val same = cand("x.mp3", info("Yesterday", "Beatles", "Help!"))
        val noAlbum = cand("y.mp3", info("yesterday", "the beatles", null))
        val other = cand("z.mp3", info("Yesterday", "The Beatles", "1"))          // different album: not a match
        val twice = cand("w.mp3", info("Yesterday", "The Beatles", "1"))          // repeat within the batch
        val fromName = cand("New Song.mp3", info(null, null, null))
        SongImport.markDuplicates(listOf(same, noAlbum, other, twice, fromName), lib)
        assertEquals(Status.DUPLICATE, same.status)
        assertEquals(Status.DUPLICATE, noAlbum.status)
        assertEquals(Status.READY, other.status)
        assertEquals(Status.DUPLICATE, twice.status)
        assertEquals(Status.READY, fromName.status)
        assertEquals("New Song", fromName.title)
        assertEquals(MusicLibrary.UNKNOWN_ARTIST, fromName.artist)
    }

    @Test fun plansUniqueItunesStylePaths() {
        val existing = mutableMapOf("F00" to mutableSetOf("ABCD.mp3"), "F01" to mutableSetOf<String>(), "Other" to mutableSetOf())
        existing.remove("Other")
        val paths = (1..500).map { SongImport.planPath("mp3", existing, Random(it)) }
        assertEquals(500, paths.toSet().size)
        assertTrue(paths.all { it.matches(Regex("/iTunes_Control/Music/F0[01]/[A-Z]{4}\\.mp3")) })
        assertFalse("/iTunes_Control/Music/F00/ABCD.mp3" in paths)
        assertTrue(SongImport.planPath("m4a", mutableMapOf()).startsWith("/iTunes_Control/Music/F00/"))
        assertTrue(SongImport.isMusicFolder("F49")); assertFalse(SongImport.isMusicFolder("Fxx"))
    }

    @Test fun newPidsAreFreshAndNonZero() {
        val inUse = setOf(1L, 2L, 3L)
        val ids = SongImport.newPids(1000, inUse, Random(7))
        assertEquals(1000, ids.toSet().size)
        assertTrue(ids.none { it == 0L || it in inUse })
    }

    @Test fun formatsDurations() {
        assertEquals("3:07", SongImport.duration(187_400))
        assertEquals("1:02:03", SongImport.duration(3_723_000))
    }
}
