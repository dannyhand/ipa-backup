package us.dannyhand.ipabackup.music

/**
 * Playlists ("containers" in Apple's databases) and their songs, in order.
 *
 * iOS 5 and later (MediaLibrary.sqlitedb) and iPhone OS 3–4 (iTunes Library.itlp, Library.itdb) both
 * keep playlists in a "container" table, with the songs of each in a link table: container_items
 * (container_pid, item_pid, position) on iOS 5+, item_to_container (item_pid, container_pid,
 * physical_order) on 3–4. Built-in lists (the whole library, Purchased, Podcasts…) have a non-zero
 * distinguished_kind and are left out, as are hidden lists and playlist folders. iTunes stores the
 * current contents of smart playlists too, so those export like any other.
 *
 * On iOS 5 and 6, container_items has no item_pid column: each playlist's songs are packed into a data
 * column as 64-bit IDs, and item_to_container is a virtual table built on Apple's own SQLite module,
 * which nothing else can open. The packed IDs are decoded both ways round (little- and big-endian),
 * keeping the reading whose IDs actually exist in the item table.
 *
 * iPhone OS 1–2 (iTunesDB) is handled in ITunesDb.parsePlaylists.
 */
data class Playlist(val name: String, val itemPids: List<Long>, val smart: Boolean = false)

object Playlists {
    fun fromSql(db: SqlDb): List<Playlist> {
        val tables = db.tables()
        if ("container" !in tables) return emptyList()
        val cc = db.columns("container")
        val pidCol = listOf("container_pid", "pid").firstOrNull { it in cc } ?: return emptyList()
        val nameCol = listOf("name", "title").firstOrNull { it in cc } ?: return emptyList()
        fun c(n: String) = if (n in cc) n else "0"
        val smartCol = listOf("smart_criteria", "smart_rules").firstOrNull { it in cc }
        val rows = db.query(
            "SELECT $pidCol, $nameCol, ${c("distinguished_kind")}, ${c("is_hidden")}, ${c("smart_is_folder")}, " +
                (if (smartCol != null) "$smartCol IS NOT NULL" else "0") + ", ${c("is_folder")} FROM container",
        )
        class C(val pid: Long, val name: String)
        val keep = rows.mapNotNull { r ->
            val pid = (r[0] as? Number)?.toLong() ?: return@mapNotNull null
            val name = (r[1] as? String)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val special = ((r[2] as? Number)?.toInt() ?: 0) != 0
            val hidden = ((r[3] as? Number)?.toInt() ?: 0) != 0
            val folder = ((r[4] as? Number)?.toInt() ?: 0) != 0 || ((r[6] as? Number)?.toInt() ?: 0) != 0
            if (special || hidden || folder) null else C(pid, name) to (((r[5] as? Number)?.toInt() ?: 0) != 0)
        }
        if (keep.isEmpty()) return emptyList()

        val items = HashMap<Long, MutableList<Long>>()
        val ic0 = if ("container_items" in tables) db.columns("container_items") else emptyList()
        when {
            "container_items" in tables && "item_pid" !in ic0 && "container_pid" in ic0 -> {
                packedItems(db, ic0).forEach { (c, list) -> items.getOrPut(c) { ArrayList() } += list }
                emptyList()
            }
            "container_items" in tables -> {
                val ic = db.columns("container_items")
                val order = listOf("position", "physical_order", "ROWID").first { it == "ROWID" || it in ic }
                db.query("SELECT container_pid, item_pid FROM container_items ORDER BY container_pid, $order")
            }
            "item_to_container" in tables -> {
                val ic = db.columns("item_to_container")
                val order = listOf("physical_order", "position", "ROWID").first { it == "ROWID" || it in ic }
                db.query("SELECT container_pid, item_pid FROM item_to_container ORDER BY container_pid, $order")
            }
            else -> emptyList()
        }.forEach { r ->
            val c = (r[0] as? Number)?.toLong() ?: return@forEach
            val i = (r[1] as? Number)?.toLong() ?: return@forEach
            items.getOrPut(c) { ArrayList() } += i
        }
        return keep.map { (c, smart) -> Playlist(c.name, items[c.pid].orEmpty(), smart) }
            .sortedBy { it.name.lowercase() }
    }

    /** iOS 5/6: container_pid → its item IDs, from whichever container_items column holds packed 64-bit IDs. */
    private fun packedItems(db: SqlDb, cols: Collection<String>): Map<Long, List<Long>> {
        val known = if ("item" in db.tables()) db.query("SELECT item_pid FROM item").mapNotNull { (it[0] as? Number)?.toLong() }.toHashSet() else hashSetOf()
        val order = if ("position" in cols) "position" else "ROWID"
        for (col in cols.filter { it != "container_pid" }) {
            val rows = db.query("SELECT container_pid, \"$col\" FROM container_items ORDER BY container_pid, $order")
            if (rows.none { it[1] is ByteArray }) continue
            fun decode(le: Boolean): Map<Long, List<Long>> {
                val out = LinkedHashMap<Long, MutableList<Long>>()
                for (r in rows) {
                    val c = (r[0] as? Number)?.toLong() ?: continue
                    val b = r[1] as? ByteArray ?: continue
                    val bb = java.nio.ByteBuffer.wrap(b).order(if (le) java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
                    val list = out.getOrPut(c) { ArrayList() }
                    while (bb.remaining() >= 8) list += bb.long
                }
                return out
            }
            val le = decode(true)
            val be = decode(false)
            fun hits(m: Map<Long, List<Long>>) = m.values.sumOf { l -> l.count { it in known } }
            val best = if (hits(be) > hits(le)) be else le
            if (known.isEmpty() || hits(best) > 0) return best
        }
        return emptyMap()
    }

    /**
     * An extended M3U playlist (UTF-8) whose entries point at where the music browser saves each song:
     * playlists go in Download/IPA Backup/Playlists, songs in ../Music/Artist - Title.ext (or ../Podcasts
     * etc.), so the playlist works once its songs are saved. Songs missing from the library are skipped.
     */
    fun m3u(playlist: Playlist, tracks: Map<Long, MusicLibrary.Track>, durationsS: Map<Long, Int> = emptyMap()): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(playlist.name).append('\n')
        for (pid in playlist.itemPids) {
            val t = tracks[pid] ?: continue
            append("#EXTINF:").append(durationsS[pid] ?: -1).append(',').append(t.artist).append(" - ").append(t.title).append('\n')
            append("../").append(t.kind.folder).append('/').append(MusicLibrary.saveName(t)).append('\n')
        }
    }
}
