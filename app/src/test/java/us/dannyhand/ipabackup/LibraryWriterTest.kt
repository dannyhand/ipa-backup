package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.LibraryWriter
import us.dannyhand.ipabackup.music.SortKeys
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager
import kotlin.random.Random

class LibraryWriterTest {
    private fun emptyLibrary(): Connection {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        val schema = javaClass.classLoader!!.getResource("medialibrary_ios6_schema.sql")!!.readText()
        c.createStatement().use { st ->
            for (stmt in schema.lines().filter { !it.startsWith("--") }.joinToString("\n").split(";\n")) if (stmt.isNotBlank()) st.execute(stmt)
            st.execute("INSERT INTO sort_map VALUES ('', 0, 0, X'31010100')")
            st.execute("INSERT INTO db_info (db_pid, primary_container_pid) VALUES (1, 777)")
            st.execute("INSERT INTO container (container_pid, name, is_hidden) VALUES (777, 'Library', 1)")
            st.execute("INSERT INTO container_items VALUES (777, X'0100000000000000')")
            st.execute("INSERT INTO entity_revision VALUES (500, 1, 0, 0, 0)")
        }
        return c
    }

    private fun song(title: String, artist: String, album: String, genre: String? = "Rock", composer: String? = null, track: Int = 1) =
        LibraryWriter.Song(title, artist, null, album, genre, composer, 2011, track, 10, 1, 1, 200_000, 44100, 256, 5_000_000,
            "F07", "ABCD.mp3", LibraryWriter.Format.MP3)

    private fun Connection.q(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getObject(it) }) } }
    }

    @Test fun addsSongsWithEverythingLinked() {
        val c = emptyLibrary()
        val w = LibraryWriter(JdbcSqlDb(c), Random(1))
        val a = w.addSong(song("Banana", "The Fruits", "Snacks", composer = "Someone"))
        val b = w.addSong(song("apple", "the fruits", "SNACKS", track = 2))          // same artist/album, other case
        val d = w.addSong(song("10 Things", "Other Band", "Numbers", genre = null))

        assertEquals(3L, (c.q("SELECT count(*) FROM item")[0][0] as Number).toLong())
        assertEquals(1L, (c.q("SELECT count(*) FROM album WHERE album != ''")[0][0] as Number).toLong() - 1) // Snacks + Numbers
        assertEquals(1, c.q("SELECT count(*) FROM item_artist WHERE item_artist LIKE '%fruits'")[0][0].let { (it as Number).toInt() })
        // Same album row for both fruit songs, and its representative is the first song.
        val albums = c.q("SELECT album_pid FROM item WHERE item_pid IN ($a, $b)").map { it[0] }
        assertEquals(albums[0], albums[1])
        assertEquals(a, (c.q("SELECT representative_item_pid FROM album WHERE album_pid = ${albums[0]}")[0][0] as Number).toLong())

        // Sort positions follow the keys: apple < Banana < 10 Things (numbers after letters).
        fun order(pid: Long) = (c.q("SELECT title_order FROM item WHERE item_pid = $pid")[0][0] as Number).toLong()
        assertTrue(order(b) < order(a) && order(a) < order(d))
        assertEquals(26, (c.q("SELECT title_order_section FROM item WHERE item_pid = $d")[0][0] as Number).toInt())
        assertEquals(1, (c.q("SELECT title_order_section FROM item WHERE item_pid = $a")[0][0] as Number).toInt())

        // Rows in every table, search mirrors the orders, no genre/composer = 0.
        for (t in listOf("item_extra", "item_stats", "item_search", "purgeable_list"))
            assertEquals(t, 3L, (c.q("SELECT count(*) FROM $t")[0][0] as Number).toLong())
        assertEquals(order(a), (c.q("SELECT search_title FROM item_search WHERE item_pid = $a")[0][0] as Number).toLong())
        assertEquals(listOf(0L, 0L), c.q("SELECT genre_id, composer_pid FROM item WHERE item_pid = $d")[0].map { (it as Number).toLong() })
        assertEquals(listOf("ABCD.mp3", 301L, 8_820_000L), c.q("SELECT location, audio_format, duration FROM item_extra WHERE item_pid = $a")[0]
            .map { if (it is Number) it.toLong() else it })
        assertEquals("iTunes_Control/Music/F07", c.q("SELECT b.path FROM item i JOIN base_location b USING (base_location_id) WHERE item_pid = $a")[0][0])
        assertEquals("MPEG audio file", c.q("SELECT k.kind FROM item i JOIN location_kind k USING (location_kind_id) WHERE item_pid = $a")[0][0])

        // Revisions: 3 songs + 1 artist "The Fruits" + 1 "Other Band" + 2 albums + 1 genre + 1 composer, numbered after the last one.
        val revs = c.q("SELECT revision, class FROM entity_revision WHERE revision > 500 ORDER BY revision")
        assertEquals(501L, (revs.first()[0] as Number).toLong())
        assertEquals(mapOf(0 to 3, 2 to 2, 3 to 1, 4 to 2, 5 to 1), revs.groupingBy { (it[1] as Number).toInt() }.eachCount())

        // Master playlist: the old entry plus the three new songs, little-endian.
        val blob = c.q("SELECT item_pid_data FROM container_items WHERE container_pid = 777")[0][0] as ByteArray
        val ids = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).let { bb -> List(blob.size / 8) { bb.long } }
        assertEquals(listOf(1L, a, b, d), ids)

        assertEquals("ok", c.q("PRAGMA integrity_check")[0][0])
    }

    @Test fun renumbersWhenNeighboursAreAdjacent() {
        val c = emptyLibrary()
        c.createStatement().use { st ->
            st.execute("INSERT INTO sort_map VALUES ('Apple', 10, 0, X'${hex(SortKeys.key("Apple"))}')")
            st.execute("INSERT INTO sort_map VALUES ('Apricot', 11, 0, X'${hex(SortKeys.key("Apricot"))}')")
            st.execute("INSERT INTO item (item_pid, title_order) VALUES (5, 11)")
        }
        val w = LibraryWriter(JdbcSqlDb(c), Random(2))
        val o = w.order("Apples")  // sorts between Apple and Apricot, which are adjacent
        val apple = (c.q("SELECT name_order FROM sort_map WHERE name = 'Apple'")[0][0] as Number).toLong()
        val apricot = (c.q("SELECT name_order FROM sort_map WHERE name = 'Apricot'")[0][0] as Number).toLong()
        assertTrue(apple < o.order && o.order < apricot)
        assertEquals(apricot, (c.q("SELECT title_order FROM item WHERE item_pid = 5")[0][0] as Number).toLong()) // reference updated
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

    /** On a copy of a real library, when one is available locally. */
    @Test fun realLibrary() {
        val src = File("/home/claude/libsample/lib.db")
        assumeTrue(src.exists())
        val tmp = File.createTempFile("medialib", ".db").apply { deleteOnExit() }
        src.copyTo(tmp, overwrite = true)
        val c = DriverManager.getConnection("jdbc:sqlite:${tmp.path}")
        val existing = c.q("SELECT a.item_artist, al.album FROM item i JOIN item_artist a USING (item_artist_pid) JOIN album al USING (album_pid) WHERE i.media_type = 8 LIMIT 1")[0]
        val beforeAlbums = (c.q("SELECT count(*) FROM album")[0][0] as Number).toLong()
        val w = LibraryWriter(JdbcSqlDb(c), Random(3))
        val pid = w.addSong(song("A Brand New Test Song", existing[0] as String, existing[1] as String))
        println("created: ${w.created}")
        assertEquals("existing artist and album are reused", beforeAlbums, (c.q("SELECT count(*) FROM album")[0][0] as Number).toLong())
        // Neighbours in sort order are the names whose keys sort just before and after.
        val order = (c.q("SELECT title_order FROM item WHERE item_pid = $pid")[0][0] as Number).toLong()
        val around = c.q("SELECT name FROM sort_map WHERE name_order BETWEEN $order - 1000000000000000 AND $order + 1000000000000000 AND name != '' ORDER BY name_order")
        println("around: ${around.map { it[0] }}")
        assertEquals("ok", c.q("PRAGMA integrity_check")[0][0])
        c.close()
    }
}
