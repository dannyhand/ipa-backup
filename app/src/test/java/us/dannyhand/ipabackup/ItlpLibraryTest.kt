package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Test
import us.dannyhand.ipabackup.music.ItlpLibrary
import us.dannyhand.ipabackup.music.MusicLibrary
import java.io.File
import java.sql.DriverManager

/** Fixture follows libgpod's itdb_sqlite.c layout (Library.itdb + Locations.itdb). */
class ItlpLibraryTest {
    @Test
    fun readsTwoFileLibrary() {
        val dir = createTempDir("itlp")
        val locFile = File(dir, "Locations.itdb")
        DriverManager.getConnection("jdbc:sqlite:${locFile.path}").use { c ->
            listOf(
                "CREATE TABLE base_location (id INTEGER PRIMARY KEY, path TEXT)",
                "CREATE TABLE location (item_pid INTEGER NOT NULL, sub_id INTEGER NOT NULL DEFAULT 0, base_location_id INTEGER DEFAULT 0, location_type INTEGER, location TEXT, extension INTEGER, kind_id INTEGER DEFAULT 0, date_created INTEGER DEFAULT 0, file_size INTEGER DEFAULT 0)",
                "INSERT INTO base_location VALUES (1, 'iTunes_Control/Music')",
                "INSERT INTO location (item_pid, base_location_id, location, file_size) VALUES (100, 1, 'F01/AAAA.mp3', 4000000), (101, 1, 'F02/BBBB.m4a', 3000000), (102, 1, 'F03/CCCC.m4v', 90000000), (103, 1, 'F04/DDDD.mp3', 1000)",
            ).forEach { c.createStatement().execute(it) }
        }
        val lib = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf(
            "CREATE TABLE genre_map (id INTEGER, genre TEXT, genre_order INTEGER)",
            "CREATE TABLE item (pid INTEGER NOT NULL, media_kind INTEGER DEFAULT 0, year INTEGER, track_number INTEGER, track_count INTEGER, disc_number INTEGER, disc_count INTEGER, genre_id INTEGER, title TEXT, artist TEXT, album TEXT, album_artist TEXT, composer TEXT)",
            "INSERT INTO genre_map VALUES (1, 'Rock', 0)",
            "INSERT INTO item VALUES (100, 1, 2009, 2, 11, 1, 1, 1, 'Song', 'Band', 'Record', 'Band', 'Writer')",
            "INSERT INTO item VALUES (101, 4, 2010, 0, 0, 0, 0, 0, 'Episode 5', 'Host', 'The Show', NULL, NULL)",
            "INSERT INTO item VALUES (102, 64, 2008, 1, 0, 0, 0, 0, 'Pilot', 'TV Series', 'Season 1', NULL, NULL)",
            "INSERT INTO item VALUES (103, 16384, 0, 0, 0, 0, 0, 0, 'Marimba', NULL, NULL, NULL, NULL)",
            "INSERT INTO item VALUES (999, 1, 0, 0, 0, 0, 0, 0, 'No file', 'X', 'Y', NULL, NULL)",
        ).forEach { lib.createStatement().execute(it) }

        val db = JdbcSqlDb(lib)
        db.exec("ATTACH DATABASE '${locFile.path.replace("'", "''")}' AS loc")
        val tracks = ItlpLibrary(db).load()

        assertEquals(4, tracks.size) // item 999 has no file
        val song = tracks.first { it.title == "Song" }
        assertEquals(MusicLibrary.Kind.MUSIC, song.kind)
        assertEquals("/iTunes_Control/Music/F01/AAAA.mp3", song.devicePath)
        assertEquals("Rock", song.genre)
        assertEquals(2009, song.year)
        assertEquals(2, song.trackNumber)
        assertEquals(4000000L, song.size)
        assertEquals("Band - Song.mp3", MusicLibrary.saveName(song))
        assertEquals(MusicLibrary.Kind.PODCAST, tracks.first { it.title == "Episode 5" }.kind)
        assertEquals("The Show - Episode 5.m4a", MusicLibrary.saveName(tracks.first { it.title == "Episode 5" }))
        assertEquals(MusicLibrary.Kind.VIDEO, tracks.first { it.title == "Pilot" }.kind)
        assertEquals(MusicLibrary.Kind.RINGTONE, tracks.first { it.title == "Marimba" }.kind)
        dir.deleteRecursively()
    }
}
