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
        when {
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
