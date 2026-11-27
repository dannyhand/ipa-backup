package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.ITunesDb
import us.dannyhand.ipabackup.music.MusicLibrary
import us.dannyhand.ipabackup.music.Playlist
import us.dannyhand.ipabackup.music.Playlists
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.DriverManager

class PlaylistsTest {
    private fun db(vararg sql: String) = JdbcSqlDb(DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        c.createStatement().use { st -> sql.forEach { st.execute(it) } }
    })

    @Test fun ios5ContainerTables() {
        val d = db(
            "CREATE TABLE container (container_pid INTEGER PRIMARY KEY, name TEXT, distinguished_kind INTEGER, is_hidden INTEGER, smart_is_folder INTEGER, smart_criteria BLOB)",
            "CREATE TABLE container_items (container_pid INTEGER, item_pid INTEGER, position INTEGER)",
            "INSERT INTO container VALUES (1, 'Library', 1, 0, 0, NULL)",          // built-in
            "INSERT INTO container VALUES (2, 'Road Trip', 0, 0, 0, NULL)",
            "INSERT INTO container VALUES (3, 'Top 25 Most Played', 0, 0, 0, X'01')", // smart
            "INSERT INTO container VALUES (4, 'Folder', 0, 0, 1, NULL)",             // folder
            "INSERT INTO container VALUES (5, 'Hidden', 0, 1, 0, NULL)",
            "INSERT INTO container_items VALUES (2, 30, 2), (2, 10, 0), (2, 20, 1), (3, 20, 0), (1, 10, 0)",
        )
        val p = Playlists.fromSql(d)
        assertEquals(listOf("Road Trip", "Top 25 Most Played"), p.map { it.name })
        assertEquals(listOf(10L, 20L, 30L), p[0].itemPids) // in position order
        assertTrue(p[1].smart)
    }

    @Test fun ios6PackedItemIds() {
        fun packed(order: ByteOrder, vararg ids: Long) = "X'" + ByteBuffer.allocate(8 * ids.size).order(order).apply { ids.forEach { putLong(it) } }
            .array().joinToString("") { "%02X".format(it) } + "'"
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val big1 = -4_611_686_018_427_387_901L; val big2 = 1_234_567_890_123L
            val d = db(
                "CREATE TABLE item (item_pid INTEGER PRIMARY KEY)",
                "INSERT INTO item VALUES ($big1), ($big2), (77)",
                "CREATE TABLE container (container_pid INTEGER PRIMARY KEY, name TEXT, distinguished_kind INTEGER)",
                "INSERT INTO container VALUES (5, 'Mix', 0)",
                "CREATE TABLE container_items (container_pid INTEGER PRIMARY KEY, item_pid_data BLOB, item_pid_data_size INTEGER)",
                "INSERT INTO container_items VALUES (5, ${packed(order, big2, big1, 77)}, 24)",
            )
            assertEquals(listOf(Playlist("Mix", listOf(big2, big1, 77L))), Playlists.fromSql(d))
        }
    }

    @Test fun ios34ItemToContainer() {
        val d = db(
            "CREATE TABLE container (pid INTEGER PRIMARY KEY, name TEXT, distinguished_kind INTEGER)",
            "CREATE TABLE item_to_container (item_pid INTEGER, container_pid INTEGER, physical_order INTEGER)",
            "INSERT INTO container VALUES (7, 'Gym', 0)",
            "INSERT INTO item_to_container VALUES (5, 7, 1), (4, 7, 0)",
        )
        assertEquals(listOf(Playlist("Gym", listOf(4L, 5L))), Playlists.fromSql(d))
        assertTrue(Playlists.fromSql(db("CREATE TABLE item (x)")).isEmpty())
    }

    @Test fun m3uPointsAtSavedSongs() {
        val t = MusicLibrary.Track(10, "Song", "Band", "Album", "/iTunes_Control/Music/F01/AB.m4a", 1, 1)
        val pod = MusicLibrary.Track(20, "Ep 1", "Host", "Show", "/iTunes_Control/Music/F02/CD.mp3", 1, 0, kind = MusicLibrary.Kind.PODCAST)
        val m = Playlists.m3u(Playlist("Mix", listOf(10, 99, 20)), mapOf(10L to t, 20L to pod))
        assertEquals("#EXTM3U\n#PLAYLIST:Mix\n#EXTINF:-1,Band - Song\n../Music/Band - Song.m4a\n" +
            "#EXTINF:-1,Host - Ep 1\n../Podcasts/Show - Ep 1.mp3\n", m)
    }

    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
    private fun mhodTitle(text: String): ByteArray {
        val s = text.toByteArray(Charsets.UTF_16LE)
        return le(40 + s.size).put("mhod".toByteArray()).putInt(0x18).putInt(40 + s.size).putInt(1).putInt(0).putInt(0)
            .putInt(1).putInt(s.size).putInt(1).putInt(0).put(s).array()
    }
    private fun mhip(trackId: Int) = le(0x4C).put("mhip".toByteArray()).putInt(0x4C).putInt(0x4C).also { it.putInt(24, trackId) }.array()
    private fun mhyp(name: String, hidden: Boolean, vararg ids: Int): ByteArray {
        val body = mhodTitle(name) + ids.fold(ByteArray(0)) { a, i -> a + mhip(i) }
        val h = le(0x6C).put("mhyp".toByteArray()).putInt(0x6C).putInt(0x6C + body.size).putInt(1).putInt(ids.size)
        h.put(20, if (hidden) 1 else 0)
        return h.array() + body
    }

    @Test fun iTunesDbPlaylists() {
        val lists = mhyp("Library", true, 1, 2, 3) + mhyp("Favorites", false, 3, 1)
        val mhlp = le(0x5C).put("mhlp".toByteArray()).putInt(0x5C).putInt(2).array() + lists
        val mhsd = le(0x60).put("mhsd".toByteArray()).putInt(0x60).putInt(0x60 + mhlp.size).putInt(2).array() + mhlp
        val hdr = le(0xBC).put("mhbd".toByteArray()).putInt(0xBC).putInt(0xBC + mhsd.size).putInt(1).putInt(0x30).putInt(1).array()
        val p = ITunesDb.parsePlaylists(hdr + mhsd)
        assertEquals(listOf(Playlist("Favorites", listOf(3L, 1L))), p)
    }
}
