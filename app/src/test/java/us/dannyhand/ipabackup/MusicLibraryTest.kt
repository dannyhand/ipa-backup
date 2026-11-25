package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.MusicLibrary
import us.dannyhand.ipabackup.music.SqlDb
import java.sql.Connection
import java.sql.DriverManager

class JdbcSqlDb(private val c: Connection) : SqlDb {
    override fun tables() = c.createStatement().executeQuery(
        "SELECT name FROM sqlite_master WHERE type IN ('table','view')").use { rs ->
        buildSet { while (rs.next()) add(rs.getString(1)) }
    }
    override fun columns(table: String) = c.createStatement().executeQuery("PRAGMA table_info(\"$table\")").use { rs ->
        buildSet { while (rs.next()) add(rs.getString(2)) }
    }
    override fun query(sql: String) = c.createStatement().executeQuery(sql).use { rs ->
        val n = rs.metaData.columnCount
        buildList { while (rs.next()) add(List(n) { i -> rs.getObject(i + 1) }) }
    }
}

class MusicLibraryTest {
    private fun db(vararg statements: String): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
            statements.forEach { c.createStatement().execute(it) }
        }

    /** iOS 6 style: title/location in item_extra, names in separate tables. */
    @Test
    fun ios6StyleSchema() {
        val c = db(
            "CREATE TABLE item (item_pid INTEGER PRIMARY KEY, media_type INTEGER, item_artist_pid INTEGER, album_artist_pid INTEGER, album_pid INTEGER, base_location_id INTEGER, track_number INTEGER)",
            "CREATE TABLE item_extra (item_pid INTEGER PRIMARY KEY, title TEXT, location TEXT, file_size INTEGER)",
            "CREATE TABLE item_artist (item_artist_pid INTEGER PRIMARY KEY, item_artist TEXT)",
            "CREATE TABLE album_artist (album_artist_pid INTEGER PRIMARY KEY, album_artist TEXT)",
            "CREATE TABLE album (album_pid INTEGER PRIMARY KEY, album TEXT)",
            "CREATE TABLE base_location (base_location_id INTEGER PRIMARY KEY, path TEXT)",
            "INSERT INTO base_location VALUES (1, 'iTunes_Control/Music/F12'), (2, 'iTunes_Control/Music/F03')",
            "INSERT INTO item_artist VALUES (1, 'Daft Punk'), (2, '')",
            "INSERT INTO album_artist VALUES (1, 'Various Artists')",
            "INSERT INTO album VALUES (1, 'Discovery'), (2, 'Mix Tape')",
            // pid, type, artist, album artist, album, base, track
            "INSERT INTO item VALUES (10, 1, 1, NULL, 1, 1, 2), (11, 1, 1, NULL, 1, 1, 1), (12, 1, 2, 1, 2, 2, 1), (13, 1, NULL, NULL, NULL, 2, 0), (14, 1, 1, NULL, 1, 1, 3), (15, 1024, 1, NULL, 1, 1, 0)",
            "INSERT INTO item_extra VALUES (10, 'Digital Love', 'ABCD.mp3', 5000000), (11, 'One More Time', 'EFGH.m4a', 6000000), (12, 'Track: \"Mix\"', 'IJKL.mp3', 100), (13, NULL, 'MNOP.m4p', 200), (14, 'Cloud Song', '', 0), (15, 'A Video', 'QRST.m4v', 999)",
        )
        val all = MusicLibrary(JdbcSqlDb(c)).load()
        // media_type 1024 = video podcast: kept for Other Media, not shown as music.
        assertEquals(MusicLibrary.Kind.PODCAST, all.single { it.title == "A Video" }.kind)
        val tracks = all.filter { it.kind == MusicLibrary.Kind.MUSIC }
        assertEquals(listOf("One More Time", "Digital Love", "MNOP", "Track: \"Mix\""), tracks.map { it.title })

        val first = tracks[0]
        assertEquals("Daft Punk", first.artist)
        assertEquals("Discovery", first.album)
        assertEquals("/iTunes_Control/Music/F12/EFGH.m4a", first.devicePath)
        assertEquals(6000000L, first.size)
        assertEquals("Daft Punk - One More Time.m4a", MusicLibrary.fileName(first))

        // Empty item artist falls back to the album artist; bad filename characters replaced.
        val mix = tracks.first { it.pid == 12L }
        assertEquals("Various Artists", mix.artist)
        assertEquals("Various Artists - Track_ _Mix_.mp3", MusicLibrary.fileName(mix))

        // No metadata at all.
        val bare = tracks.first { it.pid == 13L }
        assertEquals(MusicLibrary.UNKNOWN_ARTIST, bare.artist)
        assertEquals(MusicLibrary.UNKNOWN_ALBUM, bare.album)
        assertTrue(bare.isDrm)
    }

    /** Older layout: everything in the item table, no separate name tables. */
    @Test
    fun flatSchema() {
        val c = db(
            "CREATE TABLE item (item_pid INTEGER PRIMARY KEY, title TEXT, artist TEXT, album TEXT, location TEXT)",
            "INSERT INTO item VALUES (1, 'Song', 'Band', 'LP', 'iTunes_Control/Music/F00/X.mp3')",
        )
        val t = MusicLibrary(JdbcSqlDb(c)).load().single()
        assertEquals("Band", t.artist); assertEquals("LP", t.album)
        assertEquals("/iTunes_Control/Music/F00/X.mp3", t.devicePath)
    }

    @Test
    fun uniqueNames() {
        val used = HashSet<String>()
        assertEquals("A - B.mp3", MusicLibrary.uniqueName("A - B.mp3", used))
        assertEquals("A - B (2).mp3", MusicLibrary.uniqueName("A - B.mp3", used))
        assertEquals("a - b (3).mp3", MusicLibrary.uniqueName("a - b.mp3", used)) // storage is case-insensitive
    }

    @Test
    fun mediaKindsAndNames() {
        val c = db(
            "CREATE TABLE item (item_pid INTEGER PRIMARY KEY, media_type INTEGER, title TEXT, artist TEXT, album TEXT, location TEXT, genre TEXT)",
            "INSERT INTO item VALUES (1, 1, 'Song', 'Band', 'LP', 'iTunes_Control/Music/F00/A.mp3', 'Rock')",
            "INSERT INTO item VALUES (2, 2, 'Episode 12', 'Host', 'My Show', 'iTunes_Control/Music/F00/B.mp3', 'Podcast')",
            "INSERT INTO item VALUES (3, 4, 'Chapter 1', 'Author', 'Big Book', 'iTunes_Control/Music/F00/C.m4b', NULL)",
            "INSERT INTO item VALUES (4, 512, 'Pilot', 'The Show', 'Season 1', 'iTunes_Control/Music/F00/D.m4v', NULL)",
            "INSERT INTO item VALUES (5, 1024, 'Video Ep', 'Host', 'Vid Show', 'iTunes_Control/Music/F00/E.m4v', NULL)",
            "INSERT INTO item VALUES (6, 0, 'Home Clip', NULL, NULL, 'iTunes_Control/Music/F00/F.mov', NULL)",
            "INSERT INTO item VALUES (7, 8, 'Lecture 3', 'Prof', 'CS 101', 'iTunes_Control/Music/F00/G.m4a', NULL)",
        )
        val byTitle = MusicLibrary(JdbcSqlDb(c)).load().associateBy { it.title }
        assertEquals(MusicLibrary.Kind.MUSIC, byTitle["Song"]!!.kind)
        assertEquals(MusicLibrary.Kind.PODCAST, byTitle["Episode 12"]!!.kind)
        assertEquals(MusicLibrary.Kind.AUDIOBOOK, byTitle["Chapter 1"]!!.kind)
        assertEquals(MusicLibrary.Kind.VIDEO, byTitle["Pilot"]!!.kind)
        assertEquals(MusicLibrary.Kind.PODCAST, byTitle["Video Ep"]!!.kind)
        assertEquals(MusicLibrary.Kind.VIDEO, byTitle["Home Clip"]!!.kind)
        assertEquals(MusicLibrary.Kind.ITUNES_U, byTitle["Lecture 3"]!!.kind)
        assertEquals("Band - Song.mp3", MusicLibrary.saveName(byTitle["Song"]!!))
        assertEquals("My Show - Episode 12.mp3", MusicLibrary.saveName(byTitle["Episode 12"]!!))
        assertEquals("Author - Chapter 1.m4b", MusicLibrary.saveName(byTitle["Chapter 1"]!!))
        assertEquals("The Show - Pilot.m4v", MusicLibrary.saveName(byTitle["Pilot"]!!))
        assertEquals("Home Clip.mov", MusicLibrary.saveName(byTitle["Home Clip"]!!))
    }

    @Test
    fun ringtonesFromPlist() {
        val plist = mapOf("Ringtones" to mapOf("ABCD.m4r" to mapOf("Name" to "Marimba Remix")))
        val tones = MusicLibrary.ringtones(plist, listOf("ABCD.m4r" to 300_000L, "WXYZ.m4r" to 200_000L, "notes.txt" to 5L))
        assertEquals(listOf("Marimba Remix", "WXYZ"), tones.map { it.title })
        assertEquals("/iTunes_Control/Ringtones/ABCD.m4r", tones[0].devicePath)
        assertEquals("Marimba Remix.m4r", MusicLibrary.saveName(tones[0]))
        assertTrue(tones.all { it.kind == MusicLibrary.Kind.RINGTONE })
    }

    /** Songs stored with a non-MPMediaType number (e.g. 8) must still count as music. */
    @Test
    fun majorityTypeIsMusic() {
        val rows = (1..20).joinToString(", ") { "($it, 8, 'Song $it', 'Band', 'LP', 'iTunes_Control/Music/F00/S$it.mp3', 'Rock')" }
        val c = db(
            "CREATE TABLE item (item_pid INTEGER PRIMARY KEY, media_type INTEGER, title TEXT, artist TEXT, album TEXT, location TEXT, genre TEXT)",
            "INSERT INTO item VALUES $rows",
            "INSERT INTO item VALUES (100, 4, 'Ep 1', 'Host', 'Show', 'iTunes_Control/Music/F01/P.mp3', 'Podcast')",
            "INSERT INTO item VALUES (101, 2048, 'Clip', 'Someone', 'Vids', 'iTunes_Control/Music/F01/V.m4v', NULL)",
        )
        val lib = MusicLibrary(JdbcSqlDb(c))
        val tracks = lib.load()
        assertEquals(20, tracks.count { it.kind == MusicLibrary.Kind.MUSIC })
        assertEquals(MusicLibrary.Kind.VIDEO, tracks.single { it.title == "Clip" }.kind)
        assertTrue(lib.diagnostics, lib.diagnostics.contains("music = 8"))
        assertEquals(MusicLibrary.Kind.PODCAST, tracks.single { it.title == "Ep 1" }.kind) // genre says Podcast
    }

    /** When per-item flags exist they win over media_type. */
    @Test
    fun flagsWin() {
        val c = db(
            "CREATE TABLE item (item_pid INTEGER PRIMARY KEY, media_type INTEGER, is_song INTEGER, is_podcast INTEGER, is_audio_book INTEGER, title TEXT, artist TEXT, album TEXT, location TEXT)",
            "INSERT INTO item VALUES (1, 8, 1, 0, 0, 'Song', 'Band', 'LP', 'iTunes_Control/Music/F00/A.mp3')",
            "INSERT INTO item VALUES (2, 8, 0, 1, 0, 'Episode', 'Host', 'Show', 'iTunes_Control/Music/F00/B.mp3')",
            "INSERT INTO item VALUES (3, 8, 0, 0, 1, 'Chapter', 'Author', 'Book', 'iTunes_Control/Music/F00/C.m4a')",
        )
        val byTitle = MusicLibrary(JdbcSqlDb(c)).load().associateBy { it.title }
        assertEquals(MusicLibrary.Kind.MUSIC, byTitle["Song"]!!.kind)
        assertEquals(MusicLibrary.Kind.PODCAST, byTitle["Episode"]!!.kind)
        assertEquals(MusicLibrary.Kind.AUDIOBOOK, byTitle["Chapter"]!!.kind)
    }
}
