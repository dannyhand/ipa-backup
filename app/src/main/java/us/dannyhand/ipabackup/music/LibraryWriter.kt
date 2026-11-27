package us.dannyhand.ipabackup.music

import kotlin.random.Random

/**
 * Adds songs to an iOS 5/6 MediaLibrary.sqlitedb, writing the same rows iTunes sync does (worked out
 * from a real iOS 6.1.6 library):
 *  - item: the song's links (album, artists, genre, composer, file folder, format) and, for every
 *    name, its sort position (*_order) and index section (*_order_section) from sort_map
 *  - item_extra: title, file name, size, length, format details, track/disc, year, dates
 *  - item_stats (play counts, all zero), item_search (the sort positions again), purgeable_list
 *  - album / item_artist / album_artist / genre / composer: reused when one with the same
 *    grouping_key exists (as iOS matches them), else created with this song as their representative
 *  - sort_map: each new name gets a key (SortKeys) and a position halfway between its neighbours
 *    (positions are spread across the 64-bit range, so there's room); if neighbours are ever
 *    adjacent, every position is renumbered evenly and all references updated
 *  - entity_revision: one "added" revision per new song, album, artist, genre and composer
 *    (classes 0, 4, 2, 3, 5); the master playlist (db_info.primary_container_pid) gets the song's ID
 *    appended to its packed little-endian item list
 * Not written: the per-song "integrity" value (iTunes' own, can't be made here) and artwork.
 * Dates are seconds since 2001-01-01 (Apple's reference date).
 */
class LibraryWriter(private val db: SqlDb, private val rnd: Random = Random.Default) {
    companion object {
        const val MEDIA_TYPE_MUSIC = 8
        const val MEDIA_TYPE_PODCAST = 4
        private const val APPLE_EPOCH_S = 978_307_200L
        fun appleNow() = System.currentTimeMillis() / 1000 - APPLE_EPOCH_S
    }

    enum class Format(val kind: String, val audioFormat: Int) {
        MP3("MPEG audio file", 301),
        AAC("AAC audio file", 502),
        ALAC("Apple Lossless audio file", 0),
    }

    data class Song(
        val title: String,
        val artist: String,
        val albumArtist: String?,
        val album: String,
        val genre: String?,
        val composer: String?,
        val year: Int,
        val track: Int, val trackCount: Int,
        val disc: Int, val discCount: Int,
        val durationMs: Long,
        val sampleRate: Int,
        val bitRateKbps: Int,
        val fileSize: Long,
        /** e.g. "F07" */
        val folder: String,
        /** e.g. "QWER.mp3" */
        val fileName: String,
        val format: Format,
        val mediaType: Int = MEDIA_TYPE_MUSIC,
        val mediaKind: Int = 1,
    )

    /**
     * An SQL literal. Not LibrarySample.literal: that one leaves out big blobs, and the master playlist's
     * item list is one (8 bytes per song, e.g. 53 KB for 6,600 songs).
     */
    private fun lit(v: Any?): String = when (v) {
        null -> "NULL"
        is ByteArray -> "X'" + v.joinToString("") { "%02X".format(it) } + "'"
        is Double -> java.math.BigDecimal(v).toPlainString()
        is Number -> v.toString()
        is Boolean -> if (v) "1" else "0"
        else -> "'" + v.toString().replace("'", "''") + "'"
    }
    private fun one(sql: String): Any? = db.query(sql).firstOrNull()?.firstOrNull()
    private fun long(sql: String): Long? = (one(sql) as? Number)?.toLong()

    private fun newPid(table: String, col: String): Long {
        while (true) {
            val id = rnd.nextLong()
            if (id != 0L && long("SELECT 1 FROM $table WHERE $col = $id") == null) return id
        }
    }

    private var revision: Long = -1
    private fun addRevision(pid: Long, cls: Int) {
        if (revision < 0) revision = long("SELECT max(revision) FROM entity_revision") ?: 0
        revision++
        db.exec("INSERT OR REPLACE INTO entity_revision (revision, entity_pid, deleted, class, revision_type) VALUES ($revision, $pid, 0, $cls, 0)")
    }

    /** Sort position and section for [name], adding it to sort_map if it's new. */
    class Order(val order: Long, val section: Int)

    fun order(name: String): Order {
        if (name.isEmpty()) return Order(0, 0)
        db.query("SELECT name_order, name_section FROM sort_map WHERE name = ${lit(name)}").firstOrNull()?.let {
            return Order((it[0] as Number).toLong(), (it[1] as Number).toInt())
        }
        val key = SortKeys.key(name)
        val section = SortKeys.section(name)
        var slot = freeSlot(key)
        if (slot == null) {
            renumberSortMap()
            slot = freeSlot(key) ?: throw IllegalStateException("No room in sort_map after renumbering")
        }
        db.exec("INSERT INTO sort_map (name, name_order, name_section, sort_key) VALUES (${lit(name)}, $slot, $section, ${lit(key)})")
        return Order(slot, section)
    }

    /** A position strictly between the neighbours of [key], or null when they're adjacent. */
    private fun freeSlot(key: ByteArray): Long? {
        val k = lit(key)
        // The empty name is always first (position 0) whatever its key, so it's left out of the search.
        val prev = long("SELECT name_order FROM sort_map WHERE name != '' AND sort_key < $k ORDER BY sort_key DESC LIMIT 1") ?: 0L
        val next = long("SELECT name_order FROM sort_map WHERE name != '' AND sort_key > $k ORDER BY sort_key ASC LIMIT 1") ?: Long.MAX_VALUE
        if (next - prev < 2 || next <= prev) return null
        return prev + (next - prev) / 2
    }

    /** Spreads every position evenly again (keeping key order) and updates everything that refers to them. */
    fun renumberSortMap() {
        val rows = db.query("SELECT name_order FROM sort_map WHERE name != '' ORDER BY sort_key").map { (it[0] as Number).toLong() }
        val step = Long.MAX_VALUE / (rows.size + 1)
        db.exec("CREATE TEMP TABLE IF NOT EXISTS remap (old INTEGER PRIMARY KEY, new INTEGER)")
        db.exec("DELETE FROM remap")
        rows.forEachIndexed { i, old -> db.exec("INSERT INTO remap VALUES ($old, ${step * (i + 1)})") }
        db.exec("UPDATE sort_map SET name_order = -name_order - 1 WHERE name != ''") // avoid UNIQUE clashes mid-update
        db.exec("UPDATE sort_map SET name_order = (SELECT new FROM remap WHERE old = -sort_map.name_order - 1) WHERE name != ''")
        val itemCols = listOf("title_order", "item_artist_order", "series_name_order", "album_order", "album_artist_order", "composer_order", "genre_order")
        for (c in itemCols) db.exec("UPDATE item SET $c = coalesce((SELECT new FROM remap WHERE old = item.$c), $c)")
        for (c in listOf("search_title", "search_album", "search_artist", "search_composer", "search_album_artist"))
            db.exec("UPDATE item_search SET $c = coalesce((SELECT new FROM remap WHERE old = item_search.$c), $c)")
        db.exec("UPDATE container SET name_order = coalesce((SELECT new FROM remap WHERE old = container.name_order), name_order)")
        db.exec("DROP TABLE remap")
    }

    /** An existing row with the same grouping key (how iOS matches names that differ only in case or accents), or a new one. */
    private fun entity(table: String, pidCol: String, nameCol: String, sortCol: String?, name: String, cls: Int?, extraMatch: String = "",
                       extraCols: Map<String, Any?> = emptyMap(), newItemPid: Long): Long {
        val gk = SortKeys.groupingKey(name)
        long("SELECT $pidCol FROM $table WHERE grouping_key = ${lit(gk)} $extraMatch LIMIT 1")?.let { return it }
        val pid = newPid(table, pidCol)
        val cols = linkedMapOf<String, Any?>(pidCol to pid, nameCol to name, "grouping_key" to gk, "representative_item_pid" to newItemPid)
        if (sortCol != null) cols[sortCol] = name
        cols.putAll(extraCols)
        db.exec("INSERT INTO $table (${cols.keys.joinToString()}) VALUES (${cols.values.joinToString { lit(it) }})")
        if (cls != null) addRevision(pid, cls)
        created += "$table: $name"
        return pid
    }

    /** What was newly created (for the log). */
    val created = ArrayList<String>()

    private fun lookupOrInsert(table: String, idCol: String, valueCol: String, value: String): Long {
        long("SELECT $idCol FROM $table WHERE $valueCol = ${lit(value)}")?.let { return it }
        val id = newPid(table, idCol)
        db.exec("INSERT INTO $table ($idCol, $valueCol) VALUES ($id, ${lit(value)})")
        return id
    }

    fun addSong(s: Song, now: Long = appleNow()): Long {
        val pid = newPid("item", "item_pid")
        val albumArtistName = s.albumArtist?.takeIf { it.isNotBlank() } ?: s.artist

        val artistPid = entity("item_artist", "item_artist_pid", "item_artist", "sort_item_artist", s.artist, 2, "AND series_name = ''", newItemPid = pid)
        val albumArtistPid = entity("album_artist", "album_artist_pid", "album_artist", "sort_album_artist", albumArtistName, null, newItemPid = pid)
        val albumPid = entity("album", "album_pid", "album", "sort_album", s.album, 4, "AND album_artist_pid = $albumArtistPid",
            mapOf("album_artist_pid" to albumArtistPid), newItemPid = pid)
        val genrePid = s.genre?.takeIf { it.isNotBlank() }?.let { g ->
            long("SELECT genre_id FROM genre WHERE genre = ${lit(g)}") ?: entity("genre", "genre_id", "genre", null, g, 3, newItemPid = pid)
        } ?: 0L
        val composerPid = s.composer?.takeIf { it.isNotBlank() }?.let { c ->
            entity("composer", "composer_pid", "composer", "sort_composer", c, 5, newItemPid = pid)
        } ?: 0L

        val title = order(s.title)
        val artist = order(s.artist)
        val albumArtist = order(albumArtistName)
        val album = order(s.album)
        val genre = s.genre?.takeIf { it.isNotBlank() }?.let { order(it) } ?: Order(0, 0)
        val composer = s.composer?.takeIf { it.isNotBlank() }?.let { order(it) } ?: Order(0, 0)

        val locationKind = lookupOrInsert("location_kind", "location_kind_id", "kind", s.format.kind)
        val baseLocation = lookupOrInsert("base_location", "base_location_id", "path", "iTunes_Control/Music/${s.folder}")

        val item = linkedMapOf<String, Any?>(
            "item_pid" to pid, "media_type" to s.mediaType,
            "title_order" to title.order, "title_order_section" to title.section,
            "item_artist_pid" to artistPid, "item_artist_order" to artist.order, "item_artist_order_section" to artist.section,
            "album_pid" to albumPid, "album_order" to album.order, "album_order_section" to album.section,
            "album_artist_pid" to albumArtistPid, "album_artist_order" to albumArtist.order, "album_artist_order_section" to albumArtist.section,
            "composer_pid" to composerPid, "composer_order" to composer.order, "composer_order_section" to composer.section,
            "genre_id" to genrePid, "genre_order" to genre.order, "genre_order_section" to genre.section,
            "disc_number" to s.disc, "track_number" to s.track,
            "location_kind_id" to locationKind, "base_location_id" to baseLocation,
        )
        db.exec("INSERT INTO item (${item.keys.joinToString()}) VALUES (${item.values.joinToString { lit(it) }})")

        val extra = linkedMapOf<String, Any?>(
            "item_pid" to pid, "title" to s.title, "sort_title" to s.title,
            "disc_count" to s.discCount, "track_count" to s.trackCount,
            "location" to s.fileName, "date_created" to now, "date_modified" to now,
            "file_size" to s.fileSize, "media_kind" to s.mediaKind, "year" to s.year,
            "total_time_ms" to s.durationMs.toDouble(),
            "audio_format" to s.format.audioFormat, "sample_rate" to s.sampleRate.toDouble(),
            "duration" to s.durationMs * s.sampleRate / 1000, "bit_rate" to s.bitRateKbps,
        )
        db.exec("INSERT INTO item_extra (${extra.keys.joinToString()}) VALUES (${extra.values.joinToString { lit(it) }})")
        db.exec("INSERT INTO item_stats (item_pid) VALUES ($pid)")
        db.exec("INSERT INTO item_search (item_pid, search_title, search_album, search_artist, search_composer, search_album_artist) VALUES " +
            "($pid, ${title.order}, ${album.order}, ${artist.order}, ${composer.order}, ${albumArtist.order})")
        if ("purgeable_list" in db.tables()) db.exec("INSERT OR IGNORE INTO purgeable_list (item_pid) VALUES ($pid)")
        addRevision(pid, 0)
        appendToMaster(pid)
        return pid
    }

    /** Appends the song to the master library playlist's packed item list. */
    private fun appendToMaster(pid: Long) {
        val master = long("SELECT primary_container_pid FROM db_info LIMIT 1") ?: return
        val bytes = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(pid).array()
        val current = one("SELECT item_pid_data FROM container_items WHERE container_pid = $master") as? ByteArray
        if (current == null) {
            if (long("SELECT 1 FROM container_items WHERE container_pid = $master") == null)
                db.exec("INSERT INTO container_items (container_pid, item_pid_data) VALUES ($master, ${lit(bytes)})")
            else db.exec("UPDATE container_items SET item_pid_data = ${lit(bytes)} WHERE container_pid = $master")
        } else {
            db.exec("UPDATE container_items SET item_pid_data = ${lit(current + bytes)} WHERE container_pid = $master")
        }
    }
}
