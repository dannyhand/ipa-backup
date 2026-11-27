package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import us.dannyhand.ipabackup.music.SortKeys
import java.io.File

class SortKeysTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

    /** Keys copied from a real iOS 6.1.6 MediaLibrary.sqlitedb. */
    @Test fun matchesRealKeys() {
        assertEquals("30270105018F00", hex(SortKeys.key("A")))
        assertEquals("304D352F04292F274D3D2F4B010F018F838F0A00", hex(SortKeys.key("The Beatles")))
        assertEquals("3112822CA30105010500", hex(SortKeys.key("1979")))
        assertEquals("3112810B042D4343494B042D435341011001848F818F0700", hex(SortKeys.key("3 Doors Down")))
        assertEquals("30293741274945373D434D01839D0A018F0D00", hex(SortKeys.key("Binärpilot")))
        assertEquals("31010100", hex(SortKeys.key("")))
        assertEquals(19, SortKeys.section("The Beatles"))
        assertEquals(1, SortKeys.section("(B Quiet Interlude)"))
        assertEquals(26, SortKeys.section("(500) Days of Summer"))
        assertEquals(0, SortKeys.section("À tout à l'heure"))
        assertEquals("2D27412B2F", hex(SortKeys.groupingKey("Dance")))
    }

    @Test fun ordersLikeTheMusicApp() {
        val names = listOf("10 Years", "2 Unlimited", "Zebra", "apple", "Apple Pie", "(Ain't That) Just Like Me", "The Who", "Été")
        val sorted = names.sortedWith { a, b -> compare(SortKeys.key(a), SortKeys.key(b)) }
        assertEquals(listOf("(Ain't That) Just Like Me", "apple", "Apple Pie", "Été", "The Who", "Zebra", "2 Unlimited", "10 Years"), sorted)
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    /**
     * Against a whole real library, when one is available locally (not in the repo: the names are personal).
     * Lines: rowid, section, hex key, name.
     */
    @Test fun wholeLibrary() {
        val f = File("/home/claude/libsample/keys.tsv")
        assumeTrue(f.exists())
        val rows = f.readLines().map { it.split('\t', limit = 4) }.filter { it.size == 4 }
        val recent = rows.filter { it[0].toInt() >= 10000 }
        val exact = recent.count { hex(SortKeys.key(it[3])) == it[2] }
        val sections = rows.count { SortKeys.section(it[3]) == it[1].toInt() }
        // Placement: each recent name between the same neighbours as in the real key order.
        val real = rows.map { it[2].chunked(2).map { h -> h.toInt(16).toByte() }.toByteArray() to it[3] }.sortedWith { a, b -> compare(a.first, b.first) }
        var placed = 0
        for ((idx, r) in real.withIndex()) {
            if (rows.first { it[3] == r.second }[0].toInt() < 10000) continue
            val g = SortKeys.key(r.second)
            var lo = 0; var hi = real.size
            while (lo < hi) { val mid = (lo + hi) / 2; if (compare(real[mid].first, g) < 0) lo = mid + 1 else hi = mid }
            if (lo == idx || lo == idx + 1) placed++
        }
        println("exact ${exact}/${recent.size}, sections ${sections}/${rows.size}, placed ${placed}/${recent.size}")
        assertTrue("exact $exact/${recent.size}", exact * 100 >= recent.size * 95)
        assertTrue("sections", sections * 1000 >= rows.size * 999)
        assertTrue("placed $placed/${recent.size}", placed * 100 >= recent.size * 99)
    }
}
