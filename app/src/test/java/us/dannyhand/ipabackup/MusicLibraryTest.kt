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
        val tracks = MusicLibrary(JdbcSqlDb(c)).load()
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
}
