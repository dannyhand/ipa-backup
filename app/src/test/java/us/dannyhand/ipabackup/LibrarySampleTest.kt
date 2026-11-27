package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.LibrarySample
import java.io.StringWriter
import java.sql.DriverManager

class LibrarySampleTest {
    @Test
    fun dumpReloadsWithSampledItemsAndWholeSmallTables() {
        val src = DriverManager.getConnection("jdbc:sqlite::memory:")
        src.createStatement().use { st ->
            st.execute("CREATE TABLE item (item_pid INTEGER PRIMARY KEY, media_type INTEGER, album_pid INTEGER, title TEXT)")
            st.execute("CREATE TABLE item_extra (item_pid INTEGER PRIMARY KEY, location TEXT, art BLOB)")
            st.execute("CREATE TABLE album (album_pid INTEGER PRIMARY KEY, album TEXT)")
            st.execute("CREATE INDEX item_album ON item(album_pid)")
            st.execute("CREATE TRIGGER item_del AFTER DELETE ON item BEGIN DELETE FROM item_extra WHERE item_pid = old.item_pid; END")
            for (i in 1..300) {
                st.execute("INSERT INTO item VALUES ($i, ${if (i == 7) 4 else 8}, ${i % 10}, 'Song $i''s')")
                st.execute("INSERT INTO item_extra VALUES ($i, 'F0$i/ABCD.mp3', X'0102')")
            }
            for (a in 0..9) st.execute("INSERT INTO album VALUES ($a, 'Album $a')")
            st.execute("CREATE VIRTUAL TABLE item_search USING fts5(title)") // stands in for Apple's ML3 virtual tables
        }
        val out = StringWriter()
        LibrarySample.write(JdbcSqlDb(src), out, sampleSize = 10)
        val sql = out.toString()
        assertTrue(sql.contains("--   item: 300"))
        assertTrue(sql.contains("-- virtual table"))

        val dst = DriverManager.getConnection("jdbc:sqlite::memory:")
        dst.createStatement().use { st ->
            st.executeUpdate("PRAGMA foreign_keys=OFF")
            // sqlite-jdbc runs one statement at a time; split on statement ends.
            for (stmt in sql.lines().filter { !it.startsWith("--") }.joinToString("\n").split(";\n")) if (stmt.isNotBlank()) st.execute(stmt)
        }
        fun count(t: String) = dst.createStatement().use { it.executeQuery("SELECT count(*) FROM $t").getInt(1) }
        val items = count("item")
        assertTrue(items in 10..12)                 // random sample plus one per media type
        assertEquals(items, count("item_extra"))    // linked rows follow the sample
        assertEquals(10, count("album"))            // small tables complete
        val hasPodcast = dst.createStatement().use { it.executeQuery("SELECT count(*) FROM item WHERE media_type = 4").getInt(1) }
        assertEquals(1, hasPodcast)
        val idx = dst.createStatement().use { it.executeQuery("SELECT count(*) FROM sqlite_master WHERE type IN ('index','trigger')").getInt(1) }
        assertEquals(2, idx)
        assertEquals("X'0102'", LibrarySample.literal(byteArrayOf(1, 2)))
    }
}
